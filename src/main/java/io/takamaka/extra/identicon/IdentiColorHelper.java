/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.identicon;

import io.takamaka.extra.identicon.exceptions.IdenticonException;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.utils.TkmSignUtils;
import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
/**
 *
 * /c1 color of top left corner /c2 color of top right corner /c3 color of
 * bottom left corner /c4 color of central image
 *
 * ....../----c1----------------\ /----c2----------------\
 * /----c3----------------\ /-C1-\ /-C2-\ /-C3-\ /-C4-\
 * ....../q\/q\/q\/q\/q\/q\/q\/q\ |/q\/q\/q\/q\/q\/q\/q\/q\
 * |/q\/q\/q\/q\/q\/q\/q\/q\| |
 * a5907fa2fc0fcb336d648805be46da11ab850e5c44934279c921a855ed50825a9ba7e13e893934a2c5ba14d82a08ccdf
 *
 *
 * @author giovanni.antino@h2tcoin.com
 */
@Slf4j
public class IdentiColorHelper {

    /*
 *
 * /C1 color of top left corner
 * /C2 color of top right corner
 * /C3 color of bottom left corner
 * /C4 color of central image
 * 
 * ....../----c1----------------\      /----c2----------------\      /----c3----------------\
 * /-C1-\                        /-C2-\                        /-C3-\                        /-C4-\
 * ....../q\/q\/q\/q\/q\/q\/q\/q\     |/q\/q\/q\/q\/q\/q\/q\/q\RRRR  |/q\/q\/q\/q\/q\/q\/q\/q\|    |
 * a2fc0fcb336d648805be46da11ab850e5c44934279c921a855ed50825a9ba7e13e893934a2c5ba14d82a08ccdf
   aef5629b03ab9cb7d36b75d8f9a69d30eb7ad29230725e5ca0e551b8380ba418
 * \____/|||
 *   |   |||
 *   V   |||
    Color||\-> rotation 3 and 4
 *       |\-> rotation 1 and 2
 *       V
         Square
 *
     */
    /**
     * channel from hex
     *
     * @param cc channel in hex "ff"
     * @return channel in int ff evaluate to 255
     */
    public static int cfh(String cc) {
        return Integer.parseInt(cc.substring(0, 2), 16);
    }

    public static Color fgColorFromHex(String hex) {
        return new Color(cfh(hex.substring(0, 2)), cfh(hex.substring(2, 4)), cfh(hex.substring(4, 6)));
    }

    public static Color bgColorFromHex(String hex) {
        int r = cfh(hex.substring(0, 2));
        int g = cfh(hex.substring(2, 4));
        int b = cfh(hex.substring(4, 6));
        if (r > 220 & g > 220 & b > 220) {
            return new Color(0, 0, 0);
        } else {
            return new Color(255, 255, 255);
        }
    }

    public static int[] getRotation(String hex) {
        int[] rt = new int[4];
        rt[0] = (Integer.parseInt(hex.substring(0, 1), 16)) % 4;
        rt[1] = (Integer.parseInt(hex.substring(0, 1), 16) >> 2) % 4;
        rt[2] = (Integer.parseInt(hex.substring(1, 2), 16)) % 4;
        rt[3] = (Integer.parseInt(hex.substring(1, 2), 16) >> 2) % 4;
        return rt;
    }

    public static int[][] clone(int[][] matrix) {
        int len = matrix.length;
        int ilen = matrix.length - 1;

        int[][] mirroredMatrix = new int[len][len];
        IntStream.range(0, len).forEach((int indexRow) -> {
            IntStream.range(0, len).forEach((int indexCol) -> {
                mirroredMatrix[indexRow][indexCol] = matrix[indexRow][indexCol];
            });
        });
        return mirroredMatrix;
    }

    public static int[][] mirrorVertical(int[][] matrix) {
        int len = matrix.length;
        int ilen = matrix.length - 1;

        int[][] mirroredMatrix = new int[len][len];
        IntStream.range(0, len).forEach((int indexRow) -> {
            IntStream.range(0, len).forEach((int indexCol) -> {
                mirroredMatrix[indexRow][ilen - indexCol] = matrix[indexRow][indexCol];
            });
        });
        return mirroredMatrix;
    }

    public static int[][] mirrorHorizontal(int[][] matrix) {
        int len = matrix.length;
        int ilen = matrix.length - 1;

        int[][] mirroredMatrix = new int[len][len];
        IntStream.range(0, len).forEach((int indexRow) -> {
            IntStream.range(0, len).forEach((int indexCol) -> {
                mirroredMatrix[ilen - indexRow][indexCol] = matrix[indexRow][indexCol];
            });
        });
        return mirroredMatrix;
    }

    public static int[][] mirrorHplusV(int[][] matrix) {
        int len = matrix.length;
        int ilen = matrix.length - 1;

        int[][] mirroredMatrix = new int[len][len];
        IntStream.range(0, len).forEach((int indexRow) -> {
            IntStream.range(0, len).forEach((int indexCol) -> {
                mirroredMatrix[ilen - indexRow][ilen - indexCol] = matrix[indexRow][indexCol];
            });
        });
        return mirroredMatrix;
    }

    /**
     *
     * @param hex 3 char hex string
     * @return 32x32 mirrored matrix
     */
    public static int[][] get32by32SquareBlock(String hex) {
        //select block
        int blockIndex = Integer.parseInt(hex.substring(0, 1), 16);
        int[][][] block = IdenticonManager.i().getBlocks()[blockIndex];
        //rotation index
        int[] rotation = getRotation(hex.substring(1, 3));
        int[][] topLeft = clone(block[rotation[0]]);
        //System.out.println("top left");
        //printMatrix(topLeft);
        int[][] topRight = clone(block[rotation[1]]);
        //System.out.println("top right");
        //printMatrix(topRight);
        int[][] bottomRight = clone(block[rotation[2]]);
        //System.out.println("bottom left");
        //printMatrix(bottomRight);
        int[][] bottomLeft = clone(block[rotation[3]]);
        //System.out.println("bottom right");
        //printMatrix(bottomLeft);

        return merge4Square(topLeft, topRight, bottomRight, bottomLeft);
    }

    /**
     *
     * @param hex
     * @return
     */
    public static int[][] get64by64SquareBlockSIM(String hex) {
        int[][] squareBlock32 = get32by32SquareBlock(hex);

        int[][] topLeft = clone(squareBlock32);
        int[][] topRight = mirrorVertical(squareBlock32);
        int[][] bottomRight = mirrorHplusV(squareBlock32);
        int[][] bottomLeft = mirrorHorizontal(squareBlock32);

        return merge4Square(topLeft, topRight, bottomRight, bottomLeft);
    }

    /**
     *
     * @param hex 12 char
     * @return
     */
    public static int[][] get64by64SquareBlockHIRND(String hex) {
        //int[][] squareBlock32 = get32by32SquareBlock(hex);

        int[][] topLeft = clone(get32by32SquareBlock(hex.substring(0, 3)));
        int[][] topRight = clone(get32by32SquareBlock(hex.substring(3, 6)));
        int[][] bottomRight = clone(get32by32SquareBlock(hex.substring(6, 9)));
        int[][] bottomLeft = clone(get32by32SquareBlock(hex.substring(9, 12)));

        return merge4Square(topLeft, topRight, bottomRight, bottomLeft);
    }

    /**
     *
     * @param hex 12 char
     * @return
     */
    public static int[][] get128by128SquareBlockHIRND(String hex) {
        //int[][] squareBlock32 = get32by32SquareBlock(hex);

        int[][] topLeft = clone(get64by64SquareBlockHIRND(hex.substring(0, 12)));
        int[][] topRight = clone(get64by64SquareBlockHIRND(hex.substring(12, 24)));
        int[][] bottomRight = clone(get64by64SquareBlockHIRND(hex.substring(36, 48)));
        int[][] bottomLeft = clone(get64by64SquareBlockHIRND(hex.substring(48, 60)));

        return merge4Square(topLeft, topRight, bottomRight, bottomLeft);
    }

    /**
     *
     * @param hex 60 char hex
     * @return
     */
    public static int[][] get256by256SquareBlockHIRND(String hex) {
        int[][] squareBlock128 = get128by128SquareBlockHIRND(hex.substring(0, 60));

        int[][] topLeft = clone(squareBlock128);
        int[][] topRight = mirrorVertical(squareBlock128);
        int[][] bottomRight = mirrorHplusV(squareBlock128);
        int[][] bottomLeft = mirrorHorizontal(squareBlock128);

        return merge4Square(topLeft, topRight, bottomRight, bottomLeft);
    }

    public static int[][] merge4Square(int[][] topLeft, int[][] topRight, int[][] bottomRight, int[][] bottomLeft) {
        int len = topLeft.length * 2;
        int shift = len / 2;
        int[][] res = new int[len][len];
        IntStream.range(0, len).forEach((int indexRow) -> {
            IntStream.range(0, len).forEach((int indexCol) -> {
                if (indexRow < len / 2) {
                    //top 
                    if (indexCol < len / 2) {
                        //top left matrix
                        res[indexRow][indexCol] = topLeft[indexRow][indexCol];
                    } else {
                        //top right matrix
                        res[indexRow][indexCol] = topRight[indexRow][indexCol - shift];
                    }
                } else {
                    //bottom
                    if (indexCol < len / 2) {
                        //bottom left matrix
                        res[indexRow][indexCol] = bottomLeft[indexRow - shift][indexCol];
                    } else {
                        //bottom right matrix
                        res[indexRow][indexCol] = bottomRight[indexRow - shift][indexCol - shift];
                    }
                }
            });
        });
        return res;
    }

    public static Color[][] getIdenticon256(String hex) throws IdenticonException {
        Color fg = fgColorFromHex(hex.substring(58, 64));
        Color bg = bgColorFromHex(hex.substring(58, 64));
        int[][] hrandMatrix = get256by256SquareBlockHIRND(hex.substring(0, 60));
        Color[][] pixelToColorSquareMatrix = pixelToColorSquareMatrix(fg, bg, hrandMatrix);
        return pixelToColorSquareMatrix;
    }

    public static BufferedImage scale(BufferedImage bi, float scale) {
        BufferedImage res = new BufferedImage((int) (bi.getWidth() * scale), (int) (bi.getHeight() * scale), BufferedImage.TYPE_INT_RGB);
        AffineTransform at = new AffineTransform();
        at.scale(scale, scale);
        AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BICUBIC);
        res = scaleOp.filter(bi, res);
        return res;
    }

    public static BufferedImage getAvatarByString128(String value) throws IdenticonException {
        return scale(getAvatarByString256(value), 0.5f);
    }

    public static Icon getAvatarByString128Icon(String value) throws IdenticonException {
        BufferedImage avatarByString128 = getAvatarByString128(value);
        ImageIcon imageIcon = new ImageIcon(avatarByString128);
        return imageIcon;
    }

    public static Icon getAvatarByString256Icon(String value) throws IdenticonException {
        BufferedImage avatarByString = getAvatarByString256(value);
        ImageIcon imageIcon = new ImageIcon(avatarByString);
        return imageIcon;
    }

    /**
     * sha3_256(sha3_384(qtesla_addr)) sha3_256(ed_25519)
     *
     * @param value
     * @return
     * @throws io.takamaka.extra.identicon.exceptions.IdenticonException
     */
    public static BufferedImage getAvatarByString256(String value) throws IdenticonException {
        String val = value;
        try {
            if (value.length() == 19840) {
                val = TkmSignUtils.Hash384ToHex(value);
            }
            return getAvatarByHex(TkmSignUtils.Hash256ToHex(val));
        } catch (HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException ex) {
            log.error("hash error", ex);
            throw new IdenticonException("hash error", ex);
            //return new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        }
    }

//    /**
//     *
//     * @param value address qtesla or ed
//     * @return 256*256 identicon base64url png
//     * @throws io.takamaka.extra.identicon.exceptions.IdenticonException
//     */
//    public static String getAvatarBase64URL256(String value) throws IdenticonException {
//        String res = null;
//        String val = value;
//        BufferedImage avatarByString256 = getAvatarByString256(value);
//        try {
//            if (value.length() == 19840) {
//                val = TkmSignUtils.Hash384ToHex(value);
//            }
//            val = TkmSignUtils.Hash256ToHex(val);
//        } catch (HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException ex) {
//            log.error("hash erro in get avatar", ex);
//            throw new IdenticonException("error generating url avatar", ex);
//        }
//        if (avatarByString256 != null) {
//            try {
//
//                ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                ImageIO.write(avatarByString256, "PNG", baos); //TkmSignUtils
//                res = TkmSignUtils.fromByteArrayToB64URL(baos.toByteArray());
//                baos.close();
//            } catch (IOException ex) {
//                log.error("error generating url avatar", ex);
//                throw new IdenticonException("error generating url avatar", ex);
//            }
//        }
//        return res;
//    }
    public static String getRawAvatar256(String value) throws IdenticonException {
        String res = null;
        String val = value;
        BufferedImage avatarByString256 = null;
        try {
            avatarByString256 = getAvatarByHex(TkmSignUtils.Hash256ToHex(val));
        } catch (HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException ex) {
            log.error("hash erro in getRawAvatar256", ex);
            throw new IdenticonException("hash erro getRawAvatar256", ex);
        }
        if (avatarByString256 != null) {
            try {

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(avatarByString256, "PNG", baos); //TkmSignUtils
                res = TkmSignUtils.fromByteArrayToB64URL(baos.toByteArray());
                baos.close();
            } catch (IOException ex) {
                log.error("hash erro in getRawAvatar256", ex);
                throw new IdenticonException("hash erro in getRawAvatar256", ex);
            }
        }
        return res;
    }

    public static BufferedImage getAvatarByHex(String hex) throws IdenticonException {
        int len = 256;
        BufferedImage bi = new BufferedImage(len, len, BufferedImage.TYPE_INT_RGB);
        Color fgC = fgColorFromHex(hex.substring(58, 64));
        Color bgC = bgColorFromHex(hex.substring(58, 64));
        int fg = fgC.getRGB();
        int bg = bgC.getRGB();
        int[][] hrandMatrix = get256by256SquareBlockHIRND(hex.substring(0, 60));
        ConcurrentSkipListMap<String, IdenticonException> errors = new ConcurrentSkipListMap<>();
        IntStream.range(0, len).forEach((int indexRow) -> {
            IntStream.range(0, len).forEach((int indexCol) -> {
                switch (hrandMatrix[indexRow][indexCol]) {
                    case 0:
                        bi.setRGB(indexRow, indexCol, bg);
                        break;
                    case 1:
                        bi.setRGB(indexRow, indexCol, fg);
                        break;
                    default:
                        String errMsg = "row " + indexRow + " col " + indexCol;
                        log.error("color index out of range " + errMsg);
                        errors.put(errMsg, new IdenticonException("color index out of range " + errMsg));
                }
            });
        });
        if (!errors.isEmpty()) {
            Map.Entry<String, IdenticonException> firstEntry = errors.firstEntry();
            log.error(firstEntry.getKey(), firstEntry.getValue());
            throw firstEntry.getValue();
        }
        return bi;
    }

    public static Color[][] pixelToColorSquareMatrix(Color fg, Color bg, int[][] m) throws IdenticonException {
        int len = m.length;
        Color[][] res = new Color[len][len];
        ConcurrentSkipListMap<String, IdenticonException> errors = new ConcurrentSkipListMap<>();
        IntStream.range(0, len).forEach((int indexRow) -> {
            IntStream.range(0, len).forEach((int indexCol) -> {
                switch (m[indexRow][indexCol]) {
                    case 0:
                        res[indexRow][indexCol] = bg;
                        break;
                    case 1:
                        res[indexRow][indexCol] = fg;
                        break;
                    default:
                        String errMsg = "row " + indexRow + " col " + indexCol;
                        log.error("COLOR INDEX OUT OF RANGE " + errMsg);
                        errors.put(errMsg, new IdenticonException("COLOR INDEX OUT OF RANGE " + errMsg));
                    //Log.log(Level.SEVERE, "COLOR INDEX OUT OF RANGE");
                }
            });
        });
        if (!errors.isEmpty()) {
            Map.Entry<String, IdenticonException> firstEntry = errors.firstEntry();
            log.error(firstEntry.getKey(), firstEntry.getValue());
            throw firstEntry.getValue();
        }
        return res;
    }

    public static void createIdentiMatrix(String hex) {
        Color[] foreground = new Color[4];
        Color[] background = new Color[4];

    }

//    public static void main(String[] args) {
//        String avatarBase64URL256 = getAvatarBase64URL256("yzrhYG_yVL_Cswdg6tiTEx0nTKSPwcfd75J4BP2n0C4.");
//        System.out.println("value: " + avatarBase64URL256);
//    }

    public static void printMatrix(int[][] m) {
        IntStream.range(0, m.length).forEachOrdered((int rIndex) -> {
            IntStream.range(0, m[rIndex].length).forEachOrdered((int cIndex) -> {
                System.out.print(m[rIndex][cIndex]);
            });
            System.out.println("");
        });
        System.out.println("");
    }

}
