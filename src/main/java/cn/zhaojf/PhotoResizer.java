package cn.zhaojf;

import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

public class PhotoResizer {
    public static void main(String[] args) {

        if(args[0] == null){
            System.out.println("请输入图片文件名或目录名");
            return;
        }

        File fileOrDirectory = new File(args[0]);
        if (fileOrDirectory.isDirectory()) {
            try (Stream<Path> paths = Files.walk(Paths.get(args[0]))) {
                paths.filter(Files::isRegularFile)
                    .filter(PhotoResizer::isImageFile)
                    .forEach(path -> processImageFile(path.toFile()));
            } catch (IOException e) {
                System.out.println("遍历目录时发生异常：" + e.getMessage());
            }
        } else if (fileOrDirectory.isFile()) {
            processImageFile(fileOrDirectory);
        } else {
            System.out.println("输入的不是有效的文件或目录");
        }
    }

    private static boolean isImageFile(Path path) {
        String[] imageExtensions = {"jpg", "jpeg", "png", "gif", "bmp", "tif"};
        String fileName = path.getFileName().toString().toLowerCase();
        return Arrays.stream(imageExtensions).anyMatch(fileName::endsWith);
    }

    private static void processImageFile(File inputFile) {
        try {
            // 读取原始图像
            BufferedImage originalImage = ImageIO.read(inputFile);

            // 获取原始图像的宽度和高度
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            // 计算中心点
            int centerX = originalWidth / 2;
            int centerY = originalHeight / 2;

            // 根据图像的宽度和高度来确定它是横向图像还是纵向图像
            int maxAreaWidth;
            int maxAreaHeight;
            if (originalWidth > originalHeight) {
                // 横向图像，宽高比为3:2
                maxAreaWidth = originalHeight * 3 / 2;
                maxAreaHeight = originalHeight;
            } else {
                // 纵向图像，宽高比为2:3
                maxAreaWidth = originalWidth;
                maxAreaHeight = originalWidth * 3 / 2;
            }

            // 计算最大区域的左上角坐标
            int startX = centerX - maxAreaWidth / 2;
            if (startX < 0) {
                startX = 0;
            }
            int startY = centerY - maxAreaHeight / 2;
            if (startY < 0) {
                startY = 0;
            }

            // 确保最大区域不超出原始图像的大小
            if (startX + maxAreaWidth > originalWidth) {
                maxAreaWidth = originalWidth - startX;
            }
            if (startY + maxAreaHeight > originalHeight) {
                maxAreaHeight = originalHeight - startY;
            }

            // 获取最大区域的图像
            BufferedImage maxAreaImage = originalImage.getSubimage(startX, startY, maxAreaWidth, maxAreaHeight);

            // 设定目标大小（这里以6寸照片为例）
            int targetWidth;
            int targetHeight;
            if (originalWidth > originalHeight) {
                // 横向图像，宽高比为3:2
                targetWidth = 1800; // 6寸照片的宽度（像素）
                targetHeight = 1200; // 6寸照片的高度（像素）
            } else {
                // 纵向图像，宽高比为2:3
                targetWidth = 1200; // 6寸照片的宽度（像素）
                targetHeight = 1800; // 6寸照片的高度（像素）
            }

            // 创建新的BufferedImage对象，用于存储调整大小后的图像
            BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

            // 调整图像大小
            Graphics2D g = resizedImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(maxAreaImage, 0, 0, targetWidth, targetHeight, null);
            g.dispose();

            // 获取输入文件的名称（不包括扩展名）
            String fileName = inputFile.getName().substring(0, inputFile.getName().lastIndexOf("."));

            // 创建输出目录
            File outputDirectory = new File(inputFile.getParent(), "output");
            if (!outputDirectory.exists()) {
                outputDirectory.mkdir();
            }

            // 输出调整大小后的图像到文件
            File outputFile = new File(outputDirectory, fileName + "-out.jpg");

            // 使用Apache Commons Imaging库将BufferedImage对象写入文件
            writeImage(outputFile, resizedImage, targetWidth, targetHeight);

            // 获取新图像的宽度、高度和像素数
            int newWidth = resizedImage.getWidth();
            int newHeight = resizedImage.getHeight();
            int newPixelCount = newWidth * newHeight;

            System.out.println("图片调整大小成功，并保存为 " + fileName + "out.jpg");
            System.out.println("新图像的宽度为 " + newWidth + " 像素，高度为 " + newHeight + " 像素，总像素数为 " + newPixelCount);

        } catch (IOException e) {
            System.out.println("处理图像文件时发生异常：" + e.getMessage());
        }
    }

    private static void writeImage(File outputFile, BufferedImage resizedImage, int targetWidth, int targetHeight) {
        try (OutputStream os = Files.newOutputStream(outputFile.toPath())) {
            ImageIO.write(resizedImage, "jpg", outputFile);
            addResolutionInfo(outputFile, targetWidth, targetHeight);
        } catch (Exception e) {
            System.out.println("写入图像时发生异常：" + e.getMessage());
        }
    }

    private static void addResolutionInfo(File jpegImageFile, int width, int height) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(jpegImageFile, true);
             OutputStream os = new BufferedOutputStream(fos)) {
            TiffOutputSet outputSet = new TiffOutputSet();

            // set width and height
            TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
            exifDirectory.add(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH, width);
            exifDirectory.add(TiffTagConstants.TIFF_TAG_IMAGE_LENGTH, height);

            // set resolution
            exifDirectory.add(TiffTagConstants.TIFF_TAG_XRESOLUTION, new RationalNumber(300, 1)); // 300 dpi
            exifDirectory.add(TiffTagConstants.TIFF_TAG_YRESOLUTION, new RationalNumber(300, 1)); // 300 dpi
            exifDirectory.add(TiffTagConstants.TIFF_TAG_RESOLUTION_UNIT, (short) 2); // inches

            new ExifRewriter().updateExifMetadataLossless(jpegImageFile, os, outputSet);
        }
    }
}