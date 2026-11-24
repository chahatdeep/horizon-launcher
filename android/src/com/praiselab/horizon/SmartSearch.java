package com.praiselab.horizon;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Instant answers in search: arithmetic ("18% of 240", "(4+5)*3") and unit conversions ("5 km in miles"). */
final class SmartSearch {

    // ---------- calculator ----------

    /** The result of a maths expression, or null if the query isn't one. */
    static String calc(String q) {
        String s = q.toLowerCase(Locale.ROOT).replace("×", "*").replace("÷", "/").replace("x", "*").replace(",", "").trim();
        Matcher pct = Pattern.compile("^([\\d.]+)\\s*%\\s*of\\s*([\\d.]+)$").matcher(s);
        if (pct.find()) return fmt(Double.parseDouble(pct.group(1)) / 100 * Double.parseDouble(pct.group(2)));
        if (!s.matches("[\\d.+\\-*/^()%\\s]+") || !s.matches(".*\\d.*") || !s.matches(".*[+\\-*/^%].*")) return null;
        try {
            Parser p = new Parser(s.replace(" ", ""));
            double v = p.expr();
            if (p.pos != p.s.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return fmt(v);
        } catch (Exception e) { return null; }
    }

    private static final class Parser {
        final String s;
        int pos;
        Parser(String s) { this.s = s; }
        char peek() { return pos < s.length() ? s.charAt(pos) : 0; }
        double expr() {
            double v = term();
            while (peek() == '+' || peek() == '-') { char o = s.charAt(pos++); double t = term(); v = o == '+' ? v + t : v - t; }
            return v;
        }
        double term() {
            double v = factor();
            while (peek() == '*' || peek() == '/') { char o = s.charAt(pos++); double f = factor(); v = o == '*' ? v * f : v / f; }
            return v;
        }
        double factor() {
            double b = unary();
            if (peek() == '^') { pos++; return Math.pow(b, factor()); }
            return b;
        }
        double unary() {
            if (peek() == '-') { pos++; return -unary(); }
            if (peek() == '+') { pos++; return unary(); }
            double v;
            if (peek() == '(') { pos++; v = expr(); if (peek() != ')') throw new IllegalStateException(); pos++; }
            else {
                int st = pos;
                while (Character.isDigit(peek()) || peek() == '.') pos++;
                if (st == pos) throw new IllegalStateException();
                v = Double.parseDouble(s.substring(st, pos));
            }
            if (peek() == '%') { pos++; v /= 100; }
            return v;
        }
    }

    // ---------- unit conversion ----------

    private static final Map<String, double[]> U = new HashMap<>(); // unit -> {kind, factor to base}
    private static final String[] KINDS = {"length", "weight", "volume", "speed", "data", "time"};
    private static void u(int kind, double f, String... names) { for (String n : names) U.put(n, new double[]{kind, f}); }
    static {
        u(0, 0.001, "mm", "millimeter", "millimeters", "millimetre", "millimetres");
        u(0, 0.01, "cm", "centimeter", "centimeters", "centimetre", "centimetres");
        u(0, 1, "m", "meter", "meters", "metre", "metres");
        u(0, 1000, "km", "kilometer", "kilometers", "kilometre", "kilometres");
        u(0, 0.0254, "in", "inch", "inches");
        u(0, 0.3048, "ft", "foot", "feet");
        u(0, 0.9144, "yd", "yard", "yards");
        u(0, 1609.344, "mi", "mile", "miles");
        u(1, 0.001, "g", "gram", "grams");
        u(1, 1, "kg", "kilo", "kilos", "kilogram", "kilograms");
        u(1, 0.45359237, "lb", "lbs", "pound", "pounds");
        u(1, 0.028349523, "oz", "ounce", "ounces");
        u(1, 6.35029318, "st", "stone");
        u(2, 0.001, "ml", "milliliter", "milliliters", "millilitre");
        u(2, 1, "l", "liter", "liters", "litre", "litres");
        u(2, 3.785411784, "gal", "gallon", "gallons");
        u(2, 0.2365882365, "cup", "cups");
        u(2, 0.0295735296, "floz", "fl oz");
        u(3, 1, "kmh", "km/h", "kph");
        u(3, 1.609344, "mph");
        u(3, 3.6, "m/s", "mps");
        u(3, 1.852, "knot", "knots", "kn");
        u(4, 1, "b", "byte", "bytes");
        u(4, 1e3, "kb", "kilobyte", "kilobytes");
        u(4, 1e6, "mb", "megabyte", "megabytes");
        u(4, 1e9, "gb", "gigabyte", "gigabytes");
        u(4, 1e12, "tb", "terabyte", "terabytes");
        u(5, 1, "s", "sec", "second", "seconds");
        u(5, 60, "min", "minute", "minutes");
        u(5, 3600, "h", "hr", "hour", "hours");
        u(5, 86400, "day", "days");
        u(5, 604800, "week", "weeks");
    }

    /** "5 km in miles" -> "3.107 miles", "100 f to c" -> "37.78 °C"; null if not a conversion. */
    static String convert(String q) {
        Matcher m = Pattern.compile("^(-?[\\d.,]+)\\s*([a-z°/ ]+?)\\s+(?:to|in|into|as)\\s+([a-z°/ ]+)$").matcher(q.toLowerCase(Locale.ROOT).trim());
        if (!m.find()) return null;
        double v;
        try { v = Double.parseDouble(m.group(1).replace(",", "")); } catch (Exception e) { return null; }
        String a = m.group(2).trim(), b = m.group(3).trim();
        String ta = temp(a), tb = temp(b);
        if (ta != null && tb != null) {
            double c = ta.equals("c") ? v : ta.equals("f") ? (v - 32) * 5 / 9 : v - 273.15;
            double r = tb.equals("c") ? c : tb.equals("f") ? c * 9 / 5 + 32 : c + 273.15;
            return fmt(r) + (tb.equals("k") ? " K" : tb.equals("c") ? " °C" : " °F");
        }
        double[] ua = U.get(a), ub = U.get(b);
        if (ua == null || ub == null || ua[0] != ub[0]) return null;
        return fmt(v * ua[1] / ub[1]) + " " + b;
    }

    private static String temp(String s) {
        s = s.replace("°", "").replace("degrees", "").replace("degree", "").trim();
        if (s.equals("c") || s.equals("celsius") || s.equals("centigrade")) return "c";
        if (s.equals("f") || s.equals("fahrenheit")) return "f";
        if (s.equals("k") || s.equals("kelvin")) return "k";
        return null;
    }

    static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 1e15) return String.format(Locale.getDefault(), "%,d", (long) Math.rint(v));
        String s = String.format(Locale.getDefault(), "%,.4f", v);
        return s.replaceAll("0+$", "").replaceAll("[.,]$", "");
    }
}
