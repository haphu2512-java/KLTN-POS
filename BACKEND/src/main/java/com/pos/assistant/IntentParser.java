package com.pos.assistant;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hiểu câu hỏi tiếng Việt theo từ khóa (có dấu hay không dấu đều được).
 * Không cần mạng hay API key, nên trợ lý luôn trả lời được các câu hỏi số liệu thường gặp.
 */
@Component
public class IntentParser {

    private static final Pattern BARCODE = Pattern.compile("\\b(\\d{8,14})\\b");
    private static final Pattern N_DAYS = Pattern.compile("\\b(\\d{1,3})\\s*ngay\\b");

    private static final List<String> HELP = List.of(
            "giup", "huong dan", "lam duoc gi", "biet gi", "chuc nang", "help", "hoi gi");
    private static final List<String> RESTOCK = List.of(
            "nhap them", "can nhap", "nen nhap", "goi y nhap", "de xuat nhap", "dat hang", "du bao", "nhap hang gi",
            "nhap gi");
    private static final List<String> LOW_STOCK = List.of(
            "sap het", "het hang", "ton thap", "duoi muc", "can bo sung", "it hang", "con it");
    private static final List<String> TOP = List.of(
            "ban chay", "top", "ban nhieu nhat", "chay nhat", "ban duoc nhieu");
    private static final List<String> REVENUE = List.of(
            "doanh thu", "doanh so", "loi nhuan", "lai bao nhieu", "ban duoc bao nhieu", "bao nhieu don",
            "so don", "thu duoc", "tong tien ban");
    private static final List<String> STOCK = List.of(
            "con bao nhieu", "ton kho", "con hang", "con khong", "con ko", "gia", "bao nhieu tien", "so luong",
            "tra cuu", "kiem tra");
    private static final List<String> GREETING = List.of("xin chao", "chao", "hello", "hi", "alo");

    /** Từ không thuộc tên sản phẩm, bỏ đi khi tách từ khóa tra cứu (dạng không dấu) */
    private static final Set<String> STOPWORDS = Set.of(
            "con", "bao", "nhieu", "ton", "kho", "gia", "ban", "la", "may", "hang", "khong", "ko", "so", "luong",
            "hien", "tai", "o", "chi", "nhanh", "nay", "cua", "cho", "toi", "minh", "em", "anh", "xem", "tra", "cuu",
            "kiem", "tien", "the", "nao", "sp", "san", "pham", "mat", "co", "dang", "bay", "gio", "a", "nhe", "vay",
            "oi", "voi", "di", "giup", "hoi", "mot", "va", "ma", "vach", "ma vach", "con lai", "lai", "trong", "bn");

    public ParsedQuestion parse(String message) {
        String original = message == null ? "" : message.trim();
        String text = normalize(original);

        ChatIntent intent;
        if (containsAny(text, HELP)) intent = ChatIntent.HELP;
        else if (containsAny(text, RESTOCK)) intent = ChatIntent.RESTOCK;
        else if (containsAny(text, LOW_STOCK)) intent = ChatIntent.LOW_STOCK;
        else if (containsAny(text, TOP)) intent = ChatIntent.TOP_PRODUCTS;
        else if (containsAny(text, REVENUE)) intent = ChatIntent.REVENUE;
        else if (containsAny(text, STOCK) || BARCODE.matcher(text).find()) intent = ChatIntent.STOCK_LOOKUP;
        else if (isGreeting(text)) intent = ChatIntent.GREETING;
        else intent = ChatIntent.UNKNOWN;

        boolean yesterday = hasWord(text, "hom qua");
        int days = parseDays(text, intent, yesterday);
        String keyword = intent == ChatIntent.STOCK_LOOKUP ? extractKeyword(original) : "";
        return new ParsedQuestion(intent, days, yesterday, keyword);
    }

    private int parseDays(String text, ChatIntent intent, boolean yesterday) {
        if (yesterday) return 2;
        Matcher m = N_DAYS.matcher(text);
        if (m.find()) return Math.max(1, Math.min(90, Integer.parseInt(m.group(1))));
        if (hasWord(text, "hom nay")) return 1;
        if (hasWord(text, "tuan")) return 7;
        if (hasWord(text, "thang")) return 30;
        return intent == ChatIntent.REVENUE ? 1 : 7;
    }

    /** Tách tên hoặc mã vạch sản phẩm từ câu hỏi, giữ nguyên dấu để tìm trong CSDL */
    String extractKeyword(String original) {
        Matcher bc = BARCODE.matcher(original);
        if (bc.find()) return bc.group(1);

        List<String> kept = new ArrayList<>();
        for (String raw : original.split("\\s+")) {
            String token = raw.replaceAll("[\\p{Punct}“”‘’…]", "");
            if (token.isEmpty()) continue;
            if (STOPWORDS.contains(normalize(token))) continue;
            kept.add(token);
        }
        return String.join(" ", kept).trim();
    }

    private boolean isGreeting(String text) {
        if (text.split(" ").length > 4) return false;
        return GREETING.stream().anyMatch(g -> hasWord(text, g));
    }

    private static boolean containsAny(String text, List<String> phrases) {
        return phrases.stream().anyMatch(p -> hasWord(text, p));
    }

    /** Khớp nguyên cụm từ, không khớp giữa chừng một từ khác (ví dụ "gia" không khớp "giay") */
    private static boolean hasWord(String text, String phrase) {
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    /** Chữ thường, bỏ dấu tiếng Việt, bỏ dấu câu, gộp khoảng trắng */
    static String normalize(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd');
        return n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
}
