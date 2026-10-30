# Tích điểm và đổi điểm thành viên

Tài liệu mô tả cách hệ thống KLTN POS cộng điểm, đổi điểm, hoàn điểm khi trả hàng và cách quản lý điều chỉnh điểm. Đây là phần nền của mảng **Khuyến mãi / Thành viên**; khuyến mãi theo chương trình (giảm theo sản phẩm, mua X tặng Y, voucher) và hạng thành viên sẽ xây tiếp trên nền này.

## 1. Khái niệm

| Khái niệm | Ý nghĩa |
|---|---|
| Khách thành viên | Khách có hồ sơ trong bảng `customer` (tên, SĐT duy nhất). Khách lẻ không tích điểm. |
| Số dư điểm | Cột `customer.points`. Chỉ thay đổi bằng câu `UPDATE` nguyên tử trong `CustomerRepository`, không sửa trực tiếp qua API khách hàng. |
| Sổ điểm | Bảng `point_transaction`. Mỗi lần điểm thay đổi là một dòng: loại, số điểm (+/-), số dư sau, hóa đơn liên quan, người thao tác, thời gian. |
| Giảm tay | Ô "Giảm giá" thu ngân nhập, có hạn mức và PIN quản lý (`pos.discount.cashier-max-percent`). Tách riêng với giảm do đổi điểm. |

## 2. Quy tắc mặc định

Cấu hình trong `BACKEND/src/main/resources/application.properties` (có thể ghi đè bằng biến môi trường trong `.env`):

| Thuộc tính | Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|---|
| `pos.loyalty.earn-per-amount` | `POS_LOYALTY_EARN_PER_AMOUNT` | 10000 | Mỗi 10.000đ khách **thực trả** được 1 điểm (làm tròn xuống) |
| `pos.loyalty.point-value` | `POS_LOYALTY_POINT_VALUE` | 1000 | 1 điểm đổi được 1.000đ |
| `pos.loyalty.redeem-max-percent` | `POS_LOYALTY_REDEEM_MAX_PERCENT` | 50 | Mỗi hóa đơn dùng điểm trả tối đa 50% số tiền sau giảm tay |
| `pos.loyalty.min-redeem-points` | `POS_LOYALTY_MIN_REDEEM_POINTS` | 10 | Mỗi lần đổi tối thiểu 10 điểm |

Với mặc định trên, mua 1.000.000đ được 100 điểm, tương đương 100.000đ cho lần mua sau, tức khách được hoàn lại khoảng 10% giá trị.

## 3. Thứ tự tính tiền một hóa đơn

```
Tiền hàng (subtotal)                 = Σ đơn giá niêm yết × số lượng
− Giảm tay (discount)                  thu ngân nhập, kiểm hạn mức + PIN
= Sau giảm (afterDiscount)
− Giảm do đổi điểm (pointsDiscount)  = redeemPoints × point-value
= Cần thanh toán (totalAmount)
Điểm được cộng (pointsEarned)        = floor(totalAmount / earn-per-amount)
```

- Điểm cộng tính trên số tiền **sau khi đã trừ điểm**, nên phần trả bằng điểm không sinh thêm điểm.
- Giảm do đổi điểm **không tính vào hạn mức giảm tay** của thu ngân, vì đây là quyền của khách, không cần quản lý duyệt.

## 4. Quy trình tích điểm

1. Thu ngân bấm **F3** (hoặc "Đổi") ở ô khách hàng, tìm theo tên hoặc SĐT.
2. Khách mới: bấm **+ Thêm khách mới**, nhập tên và SĐT. Khách được **lưu vào máy chủ ngay** rồi mới gắn vào đơn. SĐT được chuẩn hóa (bỏ khoảng trắng, dấu chấm, gạch; `+84`/`84` thành `0`), trùng SĐT thì báo lỗi.
3. Thanh toán (**F4**). Máy chủ:
   - kiểm tra khách tồn tại (khách không tồn tại thì từ chối với `CUSTOMER_NOT_FOUND`, không âm thầm bán thành khách lẻ);
   - lưu hóa đơn kèm `points_earned`;
   - cộng điểm bằng `UPDATE customer SET points = points + ?`;
   - ghi sổ điểm dòng `EARN`.
4. Bill in: "Điểm tích lũy đơn này: +N" và "Điểm hiện có: M". POS cũng cập nhật số dư khách trong danh sách.

## 5. Quy trình đổi điểm

1. Chọn khách. Ô khách hiển thị **Điểm hiện có**.
2. Ở khung tính tiền, dòng **Dùng điểm**: nhập số điểm hoặc bấm **Tối đa**. Màn hình hiển thị số điểm dùng được tối đa cho đơn này và số tiền được giảm.
   - Tối đa = min(số dư, floor(floor(sauGiảm × 50%) / 1.000)).
   - Dưới mức tối thiểu (10 điểm) thì không đổi được.
   - Thêm/bớt hàng hoặc đổi giảm tay sau khi nhập điểm: POS tự hạ số điểm về mức còn hợp lệ.
3. Thanh toán. Máy chủ **tính lại toàn bộ**, không tin số tiền client gửi:
   - kiểm tra mức tối thiểu và trần (`INVALID_REDEEM`);
   - trừ điểm bằng `UPDATE customer SET points = points - ? WHERE id = ? AND points >= ?`; không đủ điểm thì báo `INSUFFICIENT_POINTS` và **hủy cả hóa đơn** (cùng transaction, kho không bị trừ);
   - ghi sổ điểm dòng `REDEEM` (số âm), rồi cộng điểm mới cho phần còn phải trả (`EARN`).
4. Bill in thêm dòng "Dùng N điểm: −xxx₫".

Câu `UPDATE ... AND points >= ?` đảm bảo hai quầy cùng đổi điểm của một khách tại cùng thời điểm thì chỉ một quầy thành công, số dư không bao giờ âm.

### Ví dụ

Khách có 36 điểm, mua 1 hộp sữa 36.000đ:

| Bước | Giá trị |
|---|---|
| Tiền hàng | 36.000đ |
| Dùng tối đa | floor(18.000 / 1.000) = 18 điểm |
| Giảm do điểm | 18.000đ |
| Cần thanh toán | 18.000đ |
| Điểm được cộng | floor(18.000 / 10.000) = 1 |
| Số dư sau | 36 − 18 + 1 = 19 |

## 6. Hoàn trả hóa đơn

Khi quản lý hoàn trả một hóa đơn có khách thành viên:

1. **Trả lại điểm đã dùng** (`REFUND_REDEEM`, +`points_redeemed`).
2. **Thu hồi đúng số điểm đã cộng** cho hóa đơn đó (`REFUND_EARN`, −`points_earned`), không tính lại theo công thức hiện hành, nên đổi cấu hình tỷ lệ cũng không làm sai.
3. Nếu khách đã tiêu bớt số điểm được cộng, chỉ thu hồi phần còn lại để số dư không âm; dòng sổ điểm ghi rõ "Thu hồi x/y điểm".
4. Hóa đơn cũ (trước khi có cột `points_earned`) thì tính lại theo công thức như trước.

Ví dụ trên sau khi hoàn: 19 + 18 − 1 = 36 điểm, đúng bằng trước khi mua.

## 7. Xem sổ điểm và điều chỉnh tay

- Màn **Đối tác → Khách Hàng Thân Thiết**, bấm **Lịch sử điểm** ở dòng khách: xem mọi biến động, mới nhất trước, kèm mã hóa đơn và số dư sau mỗi lần.
- ADMIN/MANAGER có thêm ô **Điều chỉnh**: nhập số điểm (âm để trừ) và **lý do bắt buộc**. Không trừ được quá số dư. Dòng sổ điểm loại `ADJUST` lưu người thao tác.
- API tạo/sửa khách (`POST`/`PUT /api/customers`) **không nhận trường điểm**; client gửi kèm `points` cũng bị bỏ qua.

## 8. API

| Phương thức | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/loyalty/policy` | ADMIN, MANAGER, CASHIER | Quy tắc đang áp dụng |
| GET | `/api/loyalty/customers/{id}/transactions` | ADMIN, MANAGER, CASHIER | Sổ điểm của khách |
| POST | `/api/loyalty/customers/{id}/adjust` | ADMIN, MANAGER | `{ "points": -5, "reason": "..." }` |
| POST | `/api/customers` | ADMIN, MANAGER, CASHIER | `{ name, phone, address }` |
| PUT | `/api/customers/{id}` | ADMIN, MANAGER, CASHIER | Sửa tên, SĐT, địa chỉ |
| POST | `/api/invoices` | | Thêm trường `redeemPoints` (tùy chọn, cần `customerId`) |

Hóa đơn trả về thêm `pointsRedeemed`, `pointsDiscount`, `pointsEarned` và `customerPointsBalance` (số dư ngay sau khi bán, chỉ có trong phản hồi tạo hóa đơn).

Mã lỗi: `CUSTOMER_NOT_FOUND`, `CUSTOMER_REQUIRED`, `INVALID_REDEEM`, `INSUFFICIENT_POINTS`, `ADJUST_REASON_REQUIRED`, `INVALID_PHONE`, `PHONE_ALREADY_EXISTS`.

## 9. Dữ liệu

```sql
-- Hóa đơn
points_redeemed INT,            -- điểm khách dùng (null: hóa đơn cũ)
points_discount DECIMAL(14,2),  -- tiền giảm do đổi điểm
points_earned  INT,             -- điểm được cộng; hoàn trả thu hồi đúng số này

-- Sổ điểm
CREATE TABLE point_transaction (
  id, customer_id, invoice_id,
  type,            -- EARN | REDEEM | REFUND_EARN | REFUND_REDEEM | ADJUST
  points,          -- dương = cộng, âm = trừ
  balance_after, note, created_by, created_at
);
```

Hibernate (`ddl-auto=update`) tự thêm cột và bảng khi khởi động; `schema.sql` cập nhật để tham khảo.

## 10. Kiểm thử

`BACKEND/src/test/java/com/pos/loyalty/LoyaltyIntegrationTest.java` chạy trên H2 và kiểm tra: tạo khách không tự đặt điểm, chuẩn hóa và chống trùng SĐT, cộng 36 điểm cho đơn 360.000đ, đổi 18 điểm cho đơn 36.000đ, từ chối vượt trần và dưới mức tối thiểu, hoàn trả đưa số dư về 36, sổ điểm đủ 5 dòng đúng thứ tự, điều chỉnh tay không trừ quá số dư, khách không tồn tại bị từ chối.

## 11. Hướng phát triển tiếp

1. **Hạng thành viên** (Bạc, Vàng, Kim cương) theo chi tiêu 12 tháng, hệ số tích điểm theo hạng. Dùng chi tiêu để xếp hạng, điểm để đổi, nên đổi điểm không làm tụt hạng.
2. **Khuyến mãi theo chương trình**: giảm %/số tiền theo sản phẩm hoặc nhóm hàng, mua X tặng Y, hóa đơn từ X giảm Y, voucher có hạn và số lượt. Thứ tự áp dụng: KM sản phẩm → KM đơn → voucher → giảm theo hạng → đổi điểm → giảm tay.
3. **Điểm hết hạn** sau 12 tháng (thêm loại `EXPIRE` vào sổ điểm), ưu đãi sinh nhật, báo cáo hiệu quả khuyến mãi.
