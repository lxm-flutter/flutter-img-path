package club.ximeng.idea.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class FlutterImgPath extends AnAction {

    private String previewServerPort;

    final String messageTitle = "Flutter Img Path";

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        this.previewServerPort = Messages.showInputDialog("输入预览图地址本地端口",
                messageTitle, null, this.previewServerPort, null);
        if (Objects.isNull(previewServerPort) || previewServerPort.isEmpty()) {
            return;
        }
        if (!isNumber(previewServerPort)) {
            Messages.showErrorDialog("请输入正确的端口号", messageTitle);
            return;
        }
        BufferedReader reader = null;
        BufferedWriter writer = null;
        String httpServerMessage;
        try {
            Project project = event.getData(PlatformDataKeys.PROJECT);
            assert project != null;
            final String rootPath = project.getBasePath();
            // ================================= 读取 pubspec.yaml 文件 并获取图片路径列表 =================================
            File pubSpec = new File(rootPath + File.separator + "pubspec.yaml");
            if (!pubSpec.exists()) {
                Messages.showErrorDialog("pubspec.yaml does not exist", messageTitle);
                return;
            }
            // 文件每行数据集合
            List<String> lines = new ArrayList<>();
            reader = Files.newBufferedReader(pubSpec.toPath(), StandardCharsets.UTF_8);
            String str;
            while ((str = reader.readLine()) != null) {
                lines.add(str);
            }
            reader.close();
            // 是否开始
            boolean isStart = false;
            // 新 pubspec.yaml文件 每一行数据
            List<String> newPubSpec = new ArrayList<>();
            // 图片路径字段常量
            //    # assets-generator-begin  开始标识
            //    # lib/assets/home/img/*   图片父目录
            //    # assets-generator-end    结束标识
            List<String> assetsClassField = new ArrayList<>();
            for (String line : lines) {
                newPubSpec.add(line);
                if (line.contains("assets-generator-begin")) {
                    isStart = true;
                    continue;
                }
                if (line.contains("assets-generator-end")) {
                    isStart = false;
                }
                if (!isStart) {
                    continue;
                }
                // 是否是图片父目录
                if (!isWrap(line.trim(), "#", "*")) {
                    continue;
                }
                // # lib/assets/home/img/*  --> lib/assets/home/img/
                String imagesDirectory = line.replaceAll("#", "")
                        .replaceAll("\\*", "").trim();
                // 获取图片目录
                File directory = new File(rootPath + File.separator + imagesDirectory);
                if (!directory.exists()) {
                    throw new FileSystemException("Directory wrong");
                }
                // 获取图片文件列表
                File[] list = directory.listFiles();
                if (Objects.isNull(list)) {
                    continue;
                }
                for (File file : list) {
                    if (!file.isFile() || file.getAbsolutePath().contains(".DS_Store")) {
                        continue;
                    }
                    // 图片相对路径
                    String relativeImgPath = imagesDirectory + file.getName();
                    relativeImgPath = relativeImgPath.replaceAll("\\\\", "/");

                    assetsClassField.add("/// ![](http://127.0.0.1:" + previewServerPort + "/" + relativeImgPath + ")");
                    assetsClassField.add("static final String " + relativePath2Field(relativeImgPath).replace(".", "_")
                            + " = '" + relativeImgPath + "';");
                    newPubSpec.add("    - " + relativeImgPath);
                    System.out.println("BBB    - " + relativeImgPath);
                }
            }
            // ================================= 写入文件 =================================
            File r = new File(rootPath + "/lib/r.dart");
            if (r.exists()) {
                //noinspection ResultOfMethodCallIgnored
                r.delete();
            }
            //noinspection ResultOfMethodCallIgnored
            r.createNewFile();

            StringBuilder assetsClassContent = new StringBuilder("class R {\n");
            for (String line : assetsClassField) {
                assetsClassContent.append("  ").append(line).append("\n");
            }
            assetsClassContent.append("}").append("\n");
            writer = Files.newBufferedWriter(r.toPath(), StandardCharsets.UTF_8);
            writer.write(assetsClassContent.toString());
            writer.close();
            StringBuilder pubSpecContent = new StringBuilder();
            for (String line : newPubSpec) {
                pubSpecContent.append(line).append("\n");
            }
            writer = Files.newBufferedWriter(pubSpec.toPath(), StandardCharsets.UTF_8);
            writer.write(pubSpecContent.toString());
            writer.close();
            try {
                HttpServer httpServer = HttpServer.create(new InetSocketAddress(Integer.parseInt(previewServerPort)), 0);
                httpServer.createContext("/", httpExchange -> {
                    String uri = httpExchange.getRequestURI().toString();
                    int index = uri.lastIndexOf(".");
                    String substring = uri.substring(index);
                    final File file = new File(rootPath + uri);
                    if (file.exists() && file.length() > 0) {
                        httpExchange.getResponseHeaders().set("Content-Type", "text/" + substring);
                        httpExchange.sendResponseHeaders(200, file.length());
                        httpExchange.getResponseBody().write(getBytesByFile(file));
                    }
                    httpExchange.close();
                });
                httpServer.start();
                httpServerMessage = "preview server has been open(http://127.0.0.0:" + previewServerPort + ")";
            } catch (Exception exception) {
                httpServerMessage = "preview server already open(http://127.0.0.0:" + previewServerPort + ")";
            }
            LocalFileSystem.getInstance().refresh(false);

            Messages.showInfoMessage("Creating Success!\n" + httpServerMessage, messageTitle);
        } catch (Exception ex) {
            Messages.showInfoMessage(ex.getMessage(), messageTitle);
        } finally {
            try {
                if (Objects.nonNull(reader)) {
                    reader.close();
                }
                if (Objects.nonNull(writer)) {
                    writer.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 图片相对路径转换为字段名
     * lib/assets/home/img/img1.png --> libAssetsHomeImgImg1
     *
     * @param relativeImgPath 图片相对路径
     * @return 字段名
     */
    private static String relativePath2Field(String relativeImgPath) {
        StringBuilder fieldName = new StringBuilder();
        String[] split = relativeImgPath.substring(0, relativeImgPath.lastIndexOf("."))
                .split("/");
        for (int x = 0; x < split.length; x++) {
            String word = split[x];
            if (x == 0) {
                fieldName.append(word);
            } else if (word.contains("_")) {
                final String camelCase = toCamelCase(word, '_');
                fieldName.append(upperFirst(camelCase));
            } else {
                fieldName.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
            }
        }
        return fieldName.toString();
    }

    /**
     * 通过正则表达式判断字符串是否为数字
     *
     * @param str 字符串
     * @return result
     */
    private static boolean isNumber(String str) {
        Pattern pattern = Pattern.compile("^\\d{1,5}$");
        return pattern.matcher(str).matches();
    }

    /**
     * 指定字符串是否被包装
     *
     * @param str    字符串
     * @param prefix 前缀
     * @param suffix 后缀
     * @return 是否被包装
     */
    public static boolean isWrap(CharSequence str, String prefix, String suffix) {
        if (Objects.isNull(str)) {
            return false;
        }
        final String str2 = str.toString();
        return str2.startsWith(prefix) && str2.endsWith(suffix);
    }

    /**
     * 大写首字母<br>
     * 例如：str = name, return Name
     *
     * @param str 字符串
     * @return 字符串
     */
    public static String upperFirst(String str) {
        if (Objects.isNull(str)) {
            return null;
        }
        if (str.length() > 0) {
            char firstChar = str.charAt(0);
            if (Character.isLowerCase(firstChar)) {
                return Character.toUpperCase(firstChar) + str.substring(1);
            }
        }
        return str;
    }

    /**
     * 将连接符方式命名的字符串转换为驼峰式。如果转换前的下划线大写方式命名的字符串为空，则返回空字符串。
     *
     * @param name   转换前的自定义方式命名的字符串
     * @param symbol 原字符串中的连接符连接符
     * @return 转换后的驼峰式命名的字符串
     */
    public static String toCamelCase(String name, char symbol) {
        if (Objects.isNull(name)) {
            return null;
        }
        final int length = name.length();
        final StringBuilder sb = new StringBuilder(length);
        boolean upperCase = false;
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);

            if (c == symbol) {
                upperCase = true;
            } else if (upperCase) {
                sb.append(Character.toUpperCase(c));
                upperCase = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * File 转 byte[]
     *
     * @param file .
     * @return .
     */
    public byte[] getBytesByFile(File file) {
        FileInputStream inputStream = null;
        ByteArrayOutputStream byteArrayOutputStream = null;
        try {
            inputStream = new FileInputStream(file);
            byteArrayOutputStream = new ByteArrayOutputStream(1024);
            byte[] b = new byte[1024];
            int n;
            while ((n = inputStream.read(b)) != -1) {
                byteArrayOutputStream.write(b, 0, n);
            }
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (Objects.nonNull(inputStream)) {
                    inputStream.close();
                }
                if (Objects.nonNull(byteArrayOutputStream)) {
                    byteArrayOutputStream.close();
                }
            } catch (IOException ignored) {

            }
        }
        return null;
    }
}
