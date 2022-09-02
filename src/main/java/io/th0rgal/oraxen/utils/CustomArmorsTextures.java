package io.th0rgal.oraxen.utils;

import io.th0rgal.oraxen.config.Message;
import io.th0rgal.oraxen.items.ItemBuilder;
import io.th0rgal.oraxen.items.OraxenItems;
import net.kyori.adventure.text.minimessage.Template;
import org.bukkit.Color;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.*;

public class CustomArmorsTextures {

    static final int DEFAULT_RESOLUTION = 16;
    static final int HEIGHT_RATIO = 2;
    static final int WIDTH_RATIO = 4;

    private final Map<Integer, String> usedColors = new HashMap<>();
    private final List<BufferedImage> layers1 = new ArrayList<>();
    private final List<BufferedImage> layers2 = new ArrayList<>();
    private final int resolution;
    private BufferedImage layer1;
    private int layer1Width = 0;
    private BufferedImage layer2;
    private int layer2Width = 0;

    public CustomArmorsTextures() {
        this(DEFAULT_RESOLUTION);
    }

    public CustomArmorsTextures(int resolution) {
        this.resolution = resolution;
    }

    public boolean registerImage(File file) throws IOException {

        String name = file.getName();

        if (!name.endsWith(".png"))
            return false;

        if (name.startsWith("leather_layer") && !name.contains("overlay"))
            generateShaderArmor(file, name);

        if (name.equals("leather_layer_1.png")) {
            layer1 = initLayer(ImageIO.read(file));
            layer1Width += layer1.getWidth();
            return true;
        }

        if (name.equals("leather_layer_2.png")) {
            layer2 = initLayer(ImageIO.read(file));
            layer2Width += layer2.getWidth();
            return true;
        }

        generateShaderArmor(file, name);

        return name.contains("armor_layer_") && handleArmorLayer(name, file);
    }

    private int getLayerHeight() {
        return resolution * HEIGHT_RATIO;
    }

    private BufferedImage initLayer(BufferedImage original) {
        if (original.getWidth() == resolution * WIDTH_RATIO && original.getHeight() == getLayerHeight()) {
            return original;
        }
        Image scaled = original.getScaledInstance(
                resolution * WIDTH_RATIO, getLayerHeight(), Image.SCALE_DEFAULT);
        BufferedImage output = new BufferedImage(
                resolution * WIDTH_RATIO, getLayerHeight(), BufferedImage.TYPE_INT_ARGB);
        output.getGraphics().drawImage(scaled, 0, 0, null);
        return output;
    }

    private boolean handleArmorLayer(String name, File file) throws IOException {
        if (name.endsWith("_e.png"))
            return true;

        String prefix = name.split("armor_layer_")[0];
        ItemBuilder builder = null;
        for (String suffix : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            builder = OraxenItems.getItemById(prefix + suffix);
            if (builder != null)
                break;
        }
        if (builder == null) {
            Message.NO_ARMOR_ITEM.log(Template.template("name", prefix + "<part>"),
                    Template.template("armor_layer_file", name));
            return true;
        }
        BufferedImage image = initLayer(ImageIO.read(file));
        File emissiveFile = new File(file.getPath().replace(".png", "_e.png"));
        if (emissiveFile.exists()) {
            BufferedImage emissiveImage = initLayer(ImageIO.read(emissiveFile));
            image = mergeImages(image.getWidth() + emissiveImage.getWidth(),
                    image.getHeight(),
                    image, emissiveImage);
            setPixel(image.getRaster(), 2, 0, Color.fromRGB(1, 0, 0));
        }
        addPixel(image, builder, name, prefix);

        return true;
    }

    private void generateShaderArmor(File file, String name) throws IOException {
        List<String> splitName = List.of(name.split("_"));
        Path optifinePath = Path.of("plugins/Oraxen/pack/optifine/cit/armors/", splitName.get(0));
        File optifineFile = new File(optifinePath + "/" + name);
        if (!optifinePath.toFile().exists()) optifinePath.toFile().mkdirs();
        Files.copy(file.toPath(), optifineFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        String cmdProperty =
                OraxenItems.getEntries().stream().filter(e ->
                        e.getKey().contains(splitName.get(0)) && e.getValue().getOraxenMeta().getLayers().size() == 2
                ).findFirst().map(entry -> "nbt.CustomModelData=" + entry.getValue().getOraxenMeta().getCustomModelData()).orElse("");

        String armorName = optifineFile.getName().replace("armor_layer", "layer");
        File optifineProperties = new File(optifinePath + "/" + splitName.get(0) + "_" + splitName.get(1) + ".properties");
        List<String> propertiesContent = Arrays.asList(
                "type=armor",
                "items=minecraft:leather_helmet minecraft:leather_chestplate minecraft:leather_leggings minecraft:leather_boots",
                "texture.leather_layer_1=" + armorName.replace("_2.png", "_1.png"),
                "texture.leather_layer_1_overlay=" + armorName.replace("_2.png", "_1.png"),
                "texture.leather_layer_2=" + armorName.replace("_1.png", "_2.png"),
                "texture.leather_layer_2_overlay=" + armorName.replace("_1.png", "_2.png"),
                cmdProperty
        );
        if (optifineProperties.exists()) optifineProperties.delete();
        Files.write(optifineProperties.toPath(), propertiesContent, StandardCharsets.UTF_8);
    }

    private void addPixel(BufferedImage image, ItemBuilder builder, String name, String prefix) {
        Color stuffColor = builder.getColor();
        if (usedColors.containsKey(stuffColor.asRGB())) {
            String detectedPrefix = usedColors.get(stuffColor.asRGB());
            if (!detectedPrefix.equals(prefix))
                Message.DUPLICATE_ARMOR_COLOR.log(
                        Template.template("first_armor_prefix", prefix),
                        Template.template("second_armor_prefix", detectedPrefix));
        } else usedColors.put(stuffColor.asRGB(), prefix);

        setPixel(image.getRaster(), 0, 0, stuffColor);
        if (name.contains("armor_layer_1")) {
            layers1.add(image);
            layer1Width += image.getWidth();
        } else {
            layers2.add(image);
            layer2Width += image.getWidth();
        }
    }

    public boolean hasCustomArmors() {
        return !(layers1.isEmpty() || layers2.isEmpty() || layer1 == null || layer2 == null);
    }

    public InputStream getLayerOne() throws IOException {
        return getInputStream(layer1Width, getLayerHeight(), layer1, layers1);
    }

    public InputStream getLayerTwo() throws IOException {
        return getInputStream(layer2Width, getLayerHeight(), layer2, layers2);
    }

    private InputStream getInputStream(int layerWidth, int layerHeight,
                                       BufferedImage layer, List<BufferedImage> layers) throws IOException {
        layers.add(0, layer);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(mergeImages(layerWidth, layerHeight, layers.toArray(new BufferedImage[0])),
                "png", outputStream);
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    private void setPixel(WritableRaster raster, int x, int y, Color color) {
        raster.setPixel(x, y, new int[]{color.getRed(), color.getGreen(), color.getBlue(), 255});
    }

    private BufferedImage mergeImages(int width, int height, BufferedImage... images) {
        BufferedImage concatImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = concatImage.createGraphics();
        int currentWidth = 0;
        for (BufferedImage bufferedImage : images) {
            g2d.drawImage(bufferedImage, currentWidth, 0, null);
            currentWidth += bufferedImage.getWidth();
        }
        g2d.dispose();
        return concatImage;
    }


}
