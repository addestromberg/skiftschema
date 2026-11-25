package se.skiftschema.app;

import android.graphics.Color;

final class Palette {
    static final String[] IDS = {"forest", "ocean", "plum", "amber", "nord", "deep-one"};
    static final String[] LABELS = {"Skogsgrön", "Havsblå", "Plommon", "Bärnsten", "Nord", "Cthulhu Deep One"};

    final int canvas, paper, subtle, line, edge, ink, muted;
    final int quiet, weekend, off, scrim, action, onAction, mine;
    final int mineInk, hero, heroMain, heroMuted, heroBadge, heroBadgeInk, ot;
    final int otInk, vac, vacInk, warn, warnInk, blue, red;
    final int yellow, green;

    private Palette(String values) {
        String[] colors = values.split(" ");
        canvas = Color.parseColor("#" + colors[0]);
        paper = Color.parseColor("#" + colors[1]);
        subtle = Color.parseColor("#" + colors[2]);
        line = Color.parseColor("#" + colors[3]);
        edge = Color.parseColor("#" + colors[4]);
        ink = Color.parseColor("#" + colors[5]);
        muted = Color.parseColor("#" + colors[6]);
        quiet = Color.parseColor("#" + colors[7]);
        weekend = Color.parseColor("#" + colors[8]);
        off = Color.parseColor("#" + colors[9]);
        scrim = Color.parseColor("#" + colors[10]);
        action = Color.parseColor("#" + colors[11]);
        onAction = Color.parseColor("#" + colors[12]);
        mine = Color.parseColor("#" + colors[13]);
        mineInk = Color.parseColor("#" + colors[14]);
        hero = Color.parseColor("#" + colors[15]);
        heroMain = Color.parseColor("#" + colors[16]);
        heroMuted = Color.parseColor("#" + colors[17]);
        heroBadge = Color.parseColor("#" + colors[18]);
        heroBadgeInk = Color.parseColor("#" + colors[19]);
        ot = Color.parseColor("#" + colors[20]);
        otInk = Color.parseColor("#" + colors[21]);
        vac = Color.parseColor("#" + colors[22]);
        vacInk = Color.parseColor("#" + colors[23]);
        warn = Color.parseColor("#" + colors[24]);
        warnInk = Color.parseColor("#" + colors[25]);
        blue = Color.parseColor("#" + colors[26]);
        red = Color.parseColor("#" + colors[27]);
        yellow = Color.parseColor("#" + colors[28]);
        green = Color.parseColor("#" + colors[29]);
    }

    private static final Palette[][] THEMES = {
        {
            new Palette("F5F8F5 FFFFFF EEF3EF DDE6DF C7D5CA 17251F 496157 586F63 "
                    + "EEF3EF E8EEEA 6B0B1B14 08735B FFFFFF DDF2E6 1C644D 153C33 "
                    + "F0FFF6 B8DEC8 C8EFDA 174432 FCE6D7 8B421D FFF0C4 634812 "
                    + "FBE5E2 98382F 5489AF CD6C61 D8AB4F 68A784"),
            new Palette("101714 19231E 222F28 34443B 475B4E EFF8F2 ACC0B3 93A99B "
                    + "202C25 26342C A6070D09 75DBB2 10251C 273F34 A3E9C7 233B31 "
                    + "F0FFF6 BCD8CA B4EBCD 183F30 52382D FFBD90 473B22 F5D88B "
                    + "4C302E FFB1A4 83B6DA ED958A F9D881 92CAA7"),
        },
        {
            new Palette("F0F3FF FDFBFF DEE8FF CBD8F2 B8C4DE 1D2430 475369 535F75 "
                    + "E7EEFF D6E3FE 6B111824 245FA9 FFFFFF E0EDFF 214C83 1B3659 "
                    + "F2F8FF BCCFEA C6DFFD 1D3B61 FCE6D7 8B421D FFF0C4 634812 "
                    + "FBE5E2 98382F 5489AF CD6C61 D8AB4F 68A784"),
            new Palette("0D1828 172233 1C2C46 33435D 4A5A76 ECF1FF B4C4E5 99A9C9 "
                    + "1E2839 20314B A6040E1F 98C4FF 101E31 283C55 B9D7FF 233852 "
                    + "F3F8FF BDD2ED B9D8FF 1B385D 52382D FFBD90 473B22 F5D88B "
                    + "4C302E FFB1A4 83B6DA ED958A F9D881 92CAA7"),
        },
        {
            new Palette("FFEFFB FFFBFF FDDFFB EBCFEA D7BBD6 2C202C 614B62 6D576E "
                    + "FDE8FA F7DAF5 6B1F1420 783C82 FFFFFF F1E1F5 64366E 452B52 "
                    + "FEF5FF E5CBE9 EDCEF4 50305C FCE6D7 8B421D FFF0C4 634812 "
                    + "FBE5E2 98382F 5489AF CD6C61 D8AB4F 68A784"),
            new Palette("211223 2C1C2D 3B243E 533A55 6B516D FFEBFC DBB9DB BE9EBF "
                    + "332234 3F2842 A6170919 DEB2F1 281730 432F4C EBC9F6 402E49 "
                    + "FCF2FF DDBFE6 E9C8F4 48294F 52382D FFBD90 473B22 F5D88B "
                    + "4C302E FFB1A4 83B6DA ED958A F9D881 92CAA7"),
        },
        {
            new Palette("FFF2E2 FFFCF4 FAE8CC F6D29C D9B580 2C2211 674E24 6F552A "
                    + "FFEBD1 FFDEAC 6B1F1607 785200 FFFFFF F9D39A 604100 422C10 "
                    + "FFF8E9 EBD2A6 F8CD85 513600 FCE6D7 8B421D FFF0C4 634812 "
                    + "FBE5E2 98382F 5489AF CD6C61 D8AB4F 68A784"),
            new Palette("211502 2D1F08 3D2802 553E15 6F552A FFEEDA E1BF8A CAA974 "
                    + "33250E 412D05 A6160C00 FFC563 322000 52360E FFD797 39270B "
                    + "FFF8E9 EBD2A6 FFD28B 513600 52382D FFBD90 473B22 F5D88B "
                    + "4C302E FFB1A4 83B6DA ED958A F9D881 92CAA7"),
        },
        {
            new Palette("ECEFF4 F8FAFC E5E9F0 D8DEE9 B8C3D3 2E3440 434C5E 4C566A "
                    + "E1E6EE D6DEE9 6B2E3440 37577C FFFFFF D9ECF0 2C5464 3B4252 "
                    + "ECEFF4 D8DEE9 8FBCBB 213839 FCE6D7 8B421D FFF0C4 634812 "
                    + "FBE5E2 98382F 5489AF CD6C61 D8AB4F 68A784"),
            new Palette("2E3440 3B4252 434C5E 4C566A 6B7A92 ECEFF4 E5E9F0 D8DEE9 "
                    + "353D4B 414B5D A6232732 88C0D0 202936 405C67 D5F2FA 364854 "
                    + "ECEFF4 D8DEE9 8FBCBB 213839 52382D FFBD90 473B22 F5D88B "
                    + "4C302E FFB1A4 83B6DA ED958A F9D881 92CAA7"),
        },
        {
            new Palette("E8F7F1 F7FFFB D9EFE6 C6E1D4 9BC4B2 13261D 345849 406355 "
                    + "DFF3EB D2E7DB 6B060C0A 006C48 FFFFFF CDEEDC 125940 123A2A "
                    + "E6F2E8 B5C9B8 E7D5FC 462566 FCE6D7 8B421D FFF0C4 634812 "
                    + "FBE5E2 98382F 5489AF CD6C61 D8AB4F 68A784"),
            new Palette("0A1210 142019 1D3328 284638 4A6657 E6F2E8 B5C9B8 A0B5A7 "
                    + "101B16 1D2B22 A6030706 43D68C 060C0A 1F3B2E 6EE8A8 14251E "
                    + "E6F2E8 CFDFD2 BD91F5 221534 52382D FFBD90 473B22 F5D88B "
                    + "4C302E FFB1A4 83B6DA ED958A F9D881 92CAA7"),
        },
    };

    static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }

    static Palette get(String id, boolean dark) {
        return THEMES[index(id)][dark ? 1 : 0];
    }

    int vacationColor(String id) {
        return switch (id) {
            case "blue" -> blue;
            case "red" -> red;
            case "yellow" -> yellow;
            default -> green;
        };
    }
}
