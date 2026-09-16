# Cách khắc phục giao diện website bị “AI quá”

## Mục tiêu

Giữ website hiện đại và có điểm nhấn, nhưng tạo cảm giác:

**Tin cậy → thực tế → chuyên nghiệp → tự nhiên**

Thay vì:

**Futuristic → SaaS → AI → tech startup**

---

## 1. Giữ gradient nhưng làm tinh tế hơn

Không cần bỏ gradient hoàn toàn.

Nên:

- Gradient ngắn và kín, chỉ chạy nhẹ qua một phần chữ.
- Animation chậm khoảng **5–8 giây**.
- Không đổi màu quá mạnh.
- Không dùng gradient quá nhiều màu.
- Ưu tiên chuyển động nhẹ giữa màu thương hiệu và màu sáng.

### Mục tiêu

Người dùng cảm thấy heading có chiều sâu, chứ không lập tức nhận ra đó là animation.

---

## 2. Bỏ chuyển động “bay” liên tục của heading

Đây là hiệu ứng nên bỏ đầu tiên.

Không nên để heading:

> ↑ → ↓ → ↑ → ↓

liên tục.

Thay vào đó:

- Heading đứng yên.
- Chỉ để gradient chuyển động bên trong chữ.
- Nếu cần thêm tương tác, chỉ dùng chuyển động rất nhỏ khi hover.

---

## 3. Giảm Glow

Glow mạnh + gradient + animation cùng lúc rất dễ tạo cảm giác AI-generated.

Nên:

- Bỏ text-shadow mạnh.
- Glow line thật mảnh.
- Opacity thấp.
- Không pulse liên tục.
- Không blur quá lớn.

**Glow chỉ nên được nhận ra khi người dùng chú ý.**

---

## 4. Không animate mọi thành phần

Nguyên tắc:

> **1 điểm nhấn = 1 hiệu ứng.**

Ví dụ:

| Thành phần | Hiệu ứng |
|---|---|
| Heading | Gradient animation |
| Button | Hover đổi màu |
| Card | Border/shadow nhẹ |
| Image | Không animation |

Không nên để đồng thời:

- Card translate.
- Card scale.
- Button glow.
- Button gradient.
- Heading floating.
- Heading gradient.
- Shadow thay đổi.
- Nhiều hiệu ứng blur.

---

## 5. Giảm giao diện dạng pill

Quá nhiều `rounded-full` khiến website giống SaaS/AI landing page.

Nên dùng:

- Button: **6–10px**
- Card: **10–14px**
- Input: **6–8px**
- Header: không cần pill.
- Navigation: không cần bọc trong một pill lớn.

Không phải bỏ border-radius, mà là **không dùng cùng một kiểu bo góc cho mọi thứ**.

---

## 6. Làm Shadow tự nhiên hơn

Hạn chế:

- `shadow-xl`
- `shadow-2xl`
- Shadow quá rộng.
- Shadow quá tối.

Ưu tiên:

- `shadow-sm`
- `shadow-md`

Đặc biệt với:

- Card dịch vụ.
- Form đặt xe.
- Các container lớn.

Website nhà xe nên có cảm giác **gọn, chắc và rõ ràng**, thay vì mọi thành phần đều nổi mạnh khỏi nền.

---

## 7. Giảm Uppercase và Letter Spacing

Kiểu:

> DỊCH VỤ CỦA CHÚNG TÔI

kết hợp với:

> `font-bold + uppercase + tracking-wider`

rất dễ tạo cảm giác UI được sinh tự động.

Nên ưu tiên:

> **Dịch vụ của chúng tôi**

với font-weight khoảng **500–600** và letter-spacing bình thường.

Chỉ giữ uppercase cho:

- Label nhỏ.
- Badge.
- Một số thông tin cần nhấn mạnh.

---

## 8. Heading nên có tính thương hiệu

Không nên biến toàn bộ heading thành một hiệu ứng.

Thay vì heading quá kỹ thuật như:

> ĐẶT VÉ NGAY — NHANH CHÓNG • AN TOÀN • TIỆN LỢI

nên có hierarchy tự nhiên:

> **Đặt chuyến đi của bạn**

Sau đó dùng một dòng mô tả ngắn bên dưới.

Gradient chỉ nên nhấn **một vài từ quan trọng**, không nhất thiết gradient cả câu.

---

## 9. Animation nên có nhịp

Website tự nhiên thường có:

**Trang tải → đứng yên → người dùng hover → phản hồi**

thay vì:

**Trang tải → mọi thứ liên tục chuyển động.**

Đặc biệt với website **Nhà Xe Quảng Nam**, nên ưu tiên:

- Tin cậy.
- Thực tế.
- Chuyên nghiệp.
- Hiện đại vừa đủ.

---

# Công thức đề xuất

| Thành phần | Thiết lập |
|---|---|
| Font | Source Sans 3 + DM Sans |
| Heading | DM Sans 600 |
| Gradient heading | Có |
| Gradient animation | Có, rất chậm |
| Heading floating | **Bỏ** |
| Glow | Rất nhẹ |
| Glow animation | Subtle |
| Button gradient | **Bỏ** |
| Button glow | **Bỏ** |
| Card animation | Hover rất nhẹ |
| Card shadow | SM/MD |
| Border radius | 6–14px |
| Uppercase | Chỉ label nhỏ |
| Letter spacing | Gần như bình thường |
| Backdrop blur | Hạn chế |
| Glassmorphism | **Bỏ** |
| Mỗi element một animation | **Không** |

---

# Nguyên tắc quan trọng nhất

Đừng cố làm giao diện “ít AI” bằng cách bỏ hết hiệu ứng.

Cách tốt hơn là:

> **Giữ 1–2 hiệu ứng đặc trưng, nhưng làm chúng đủ tinh tế để người dùng không chú ý đến hiệu ứng.**

Với website này, nên:

1. **Giữ gradient animation ở heading** để tạo điểm nhấn thương hiệu.
2. **Bỏ floating heading**.
3. **Giảm glow**.
4. **Giảm pill UI**.
5. **Giảm shadow**.
6. **Giảm hover transform**.
7. **Giảm uppercase và letter-spacing**.
8. **Không animate tất cả các thành phần cùng lúc**.

Kết quả mong muốn:

**Hiện đại nhưng không “AI”.  
Có animation nhưng không phô.  
Có thương hiệu nhưng không quá công nghệ.  
Quan trọng nhất: nhìn giống một website doanh nghiệp thật.**
