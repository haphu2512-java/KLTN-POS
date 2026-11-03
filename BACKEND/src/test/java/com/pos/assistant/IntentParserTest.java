package com.pos.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentParserTest {

    private final IntentParser parser = new IntentParser();

    @Test
    @DisplayName("Doanh thu: có dấu, không dấu, hôm nay, hôm qua, tuần, N ngày")
    void revenue() {
        ParsedQuestion today = parser.parse("Doanh thu hôm nay bao nhiêu?");
        assertEquals(ChatIntent.REVENUE, today.intent());
        assertEquals(1, today.days());

        ParsedQuestion noAccent = parser.parse("doanh thu hom qua");
        assertEquals(ChatIntent.REVENUE, noAccent.intent());
        assertTrue(noAccent.yesterday());

        assertEquals(7, parser.parse("Doanh số tuần này").days());
        assertEquals(30, parser.parse("lợi nhuận 30 ngày").days());
        assertEquals(1, parser.parse("Hôm nay bán được bao nhiêu tiền").days());
        assertEquals(ChatIntent.REVENUE, parser.parse("Hôm nay bán được bao nhiêu tiền").intent());
    }

    @Test
    @DisplayName("Bán chạy, hàng sắp hết, gợi ý nhập, trợ giúp, chào")
    void otherIntents() {
        assertEquals(ChatIntent.TOP_PRODUCTS, parser.parse("Top bán chạy tuần này").intent());
        assertEquals(ChatIntent.LOW_STOCK, parser.parse("Hàng nào sắp hết?").intent());
        assertEquals(ChatIntent.LOW_STOCK, parser.parse("Tồn kho nào sắp hết").intent());
        assertEquals(ChatIntent.RESTOCK, parser.parse("Nên nhập thêm gì?").intent());
        assertEquals(ChatIntent.HELP, parser.parse("Bạn làm được gì?").intent());
        assertEquals(ChatIntent.GREETING, parser.parse("Xin chào").intent());
        assertEquals(ChatIntent.UNKNOWN, parser.parse("Thời tiết ngày mai thế nào").intent());
    }

    @Test
    @DisplayName("Tra tồn kho: tách tên sản phẩm giữ nguyên dấu, nhận mã vạch")
    void stockLookup() {
        ParsedQuestion q = parser.parse("Mì Hảo Hảo còn bao nhiêu?");
        assertEquals(ChatIntent.STOCK_LOOKUP, q.intent());
        assertEquals("Mì Hảo Hảo", q.keyword());

        assertEquals("sữa tươi Vinamilk", parser.parse("Giá sữa tươi Vinamilk là bao nhiêu").keyword());

        ParsedQuestion barcode = parser.parse("8934563138165");
        assertEquals(ChatIntent.STOCK_LOOKUP, barcode.intent());
        assertEquals("8934563138165", barcode.keyword());
    }
}
