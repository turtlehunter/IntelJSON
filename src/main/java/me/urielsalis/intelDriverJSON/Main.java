package me.urielsalis.intelDriverJSON;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Uriel Salischiker
 */
public class Main {

    /*
    -1 = no subdirectories
    0 = ok
    1 = no .inf
    2 = cant find info
     */

    public static HashMap<String, String> pci = new HashMap<>();
    public static HashMap<String, String> temp = new HashMap<>();

    public static int parseFolder(String path) {
        File file = new File(path);
        String[] directories = file.list(new FilenameFilter() {
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });
        System.out.println(Arrays.toString(directories));
        boolean readInf = false;
        if (directories.length != 0) {
            boolean infFound = false;
            for (String str : directories) {
                File dir = new File(file, str);
                String[] infs = dir.list(new FilenameFilter() {
                    public boolean accept(File current, String name) {
                        return name.endsWith(".inf");
                    }
                });
                System.out.println(dir.getAbsolutePath() + " " + Arrays.toString(dir.list()));

                System.out.println(dir.getAbsolutePath() + " " + Arrays.toString(infs));
                if (infs != null && infs.length != 0) {
                    infFound = true;
                    for (String s : infs) {
                        File inf = new File(dir, s);
                        try {
                            String text = new String(Files.readAllBytes(inf.toPath()), StandardCharsets.UTF_16);
                            if (text.contains("PCI")) {
                                System.out.println("true");
                                readInf = true;
                                String[] lines = text.split("\n");
                                for (String line : lines) {
                                    if (line.contains("PCI")) {
                                        temp.put(line.substring(0, line.indexOf("=")).replace("%", "").trim(), line.substring(line.indexOf(",")+2).trim());
                                    } else if (line.contains(" = \"")) {
                                        for (Map.Entry<String, String> entry : temp.entrySet()) {
                                            if (line.startsWith(entry.getKey())) {
                                                pci.put(line.split("=")[1].replace("\"", "").trim(), entry.getValue());
                                            }
                                        }
                                    }
                                }
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            if(!infFound) return 1;
        } else {
            return -1;
        }
        if(!readInf) return 2;
        return 0;
    }

    public static void main(String[] args) {
        JSONParser parser = new JSONParser();
        JSONArray out = new JSONArray();
        try {
            JSONArray array = (JSONArray) parser.parse(new String(Files.readAllBytes(new File("json.txt").toPath()), StandardCharsets.UTF_8));
            for (Object obj: array) {
                JSONObject driver = (JSONObject) obj;
                JSONArray downloads = (JSONArray) ((JSONObject) obj).get("downloads");
                JSONObject download = (JSONObject) downloads.get(0);
                String t = (String) download.get("url");
                String url = "";

                String name = "driver-"+driver.get("name");
                System.out.println(download.get("name"));
                URLConnection conn = new URL(t).openConnection();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while((line=reader.readLine())!=null) {
                        if(line.contains("data-direct-path")) {
                            String u = line.substring(line.indexOf("https://downloadmirror.intel.com"), line.indexOf("\">"));
                            if(!url.contains(".zip")) {
                                if(!url.contains("32")) {
                                    url = u;
                                }
                            }
                        }
                    }
                }


                URL website = new URL(url);
                ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                FileOutputStream fos = new FileOutputStream(name + ".zip");
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                try {
                    ZipFile zipFile = new ZipFile(name + ".zip");
                    zipFile.extractAll(name);
                    int result = parseFolder(name);

                    switch (result) {
                        case 0:
                            JSONArray j = new JSONArray();
                            JSONArray j2 = new JSONArray();
                            for(Map.Entry<String, String> entry: pci.entrySet()) {
                                JSONObject json = new JSONObject();
                                json.put("name", entry.getKey());
                                json.put("value", entry.getValue());
                                j.add(json);
                                j2.add(entry.getValue());
                            }
                            driver.put("rawPCI", j2);
                            driver.put("PCI", j);
                            out.add(driver);
                        case -1:
                            System.out.println(name + " has no subdirectories. Skipping");
                        case 1:
                            System.out.println(name + " has no .inf");
                        case 2:
                            System.out.println(name + " has no info in its .inf");
                    }
                } catch (ZipException e) {
                    e.printStackTrace();
                }
                break;
            }
        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }
        System.out.println(out.toJSONString());
    }
}
