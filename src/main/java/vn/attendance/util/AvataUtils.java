package vn.attendance.util;

import com.sun.jna.Memory;
import lombok.Getter;
import lombok.Setter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

public class AvataUtils {

    /**
     * PhÆ°Æ¡ng thá»©c Ä‘á»ƒ giáº£i mÃ£ áº£nh tá»« chuá»—i base64 vÃ  trÃ­ch xuáº¥t cÃ¡c thÃ´ng sá»‘ áº£nh
     *
     * @param base64String chuá»—i base64 cá»§a áº£nh
     * @return Ä‘á»‘i tÆ°á»£ng AvataInfo chá»©a cÃ¡c thÃ´ng sá»‘ áº£nh
     * @throws IOException náº¿u cÃ³ lá»—i khi giáº£i mÃ£ hoáº·c Ä‘á»c áº£nh
     */
    public static AvataInfo decodeBase64AndExtractInfo(String base64String) throws IOException {
        // TÃ¡ch pháº§n base64 data
        String base64Image = base64String.contains(";base64,") ? base64String.split(";base64,")[1] : base64String;

        // Giáº£i mÃ£ base64 thÃ nh máº£ng byte
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);

        // Äá»c hÃ¬nh áº£nh tá»« máº£ng byte
        ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
        BufferedImage image = ImageIO.read(bis);

        // Láº¥y chiá»u rá»™ng vÃ  chiá»u dÃ i cá»§a hÃ¬nh áº£nh
        int width = image.getWidth();
        int height = image.getHeight();

        AvataInfo avataInfo = new AvataInfo();
        avataInfo.setPictureLeng(imageBytes.length);
        avataInfo.setWidth(width);
        avataInfo.setHeight(height);
        var  memory = new Memory(imageBytes.length);
        memory.write(0, imageBytes, 0, imageBytes.length);
        avataInfo.setMemory(memory);

        return avataInfo;
    }

    public static String convertImageToBase64String(byte[] image) {
        // MÃ£ hÃ³a byte thÃ nh chuá»—i Base64
        String encodedString = Base64.getEncoder().encodeToString(image);
        return encodedString;

    }

    /**
     * Äá»‹nh nghÄ©a lá»›p AvataInfo Ä‘á»ƒ lÆ°u trá»¯ thÃ´ng tin áº£nh
     */
    @Getter
    @Setter
    public static class AvataInfo {
        private int pictureLeng;
        private int width;
        private int height;
        private Memory memory;
    }
}

