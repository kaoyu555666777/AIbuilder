package cn.kaoyu666.aibuilder.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 模型选择界面 - 从获取到的模型列表中选择一个模型
 */
public class ModelSelectScreen extends Screen {
    private final Screen parent;
    private final List<String> models;
    private final Consumer<String> onSelect;
    private int scrollOffset = 0;

    public ModelSelectScreen(Screen parent, List<String> models, Consumer<String> onSelect) {
        super(Component.translatable("aibuilder.select.title"));
        this.parent = parent;
        this.models = models;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        int centerX = this.width / 2;
        int buttonWidth = 280;
        int buttonHeight = 20;
        int spacing = 22;
        int startY = 40;
        int maxVisible = 12;

        for (int i = scrollOffset; i < Math.min(models.size(), scrollOffset + maxVisible); i++) {
            String model = models.get(i);
            int y = startY + (i - scrollOffset) * spacing;

            addRenderableWidget(Button.builder(
                            Component.literal(truncate(model, 45)),
                            btn -> {
                                onSelect.accept(model);
                            })
                    .bounds(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight)
                    .build());
        }

        // 翻页
        if (models.size() > maxVisible) {
            int navY = startY + maxVisible * spacing + 5;
            addRenderableWidget(Button.builder(Component.translatable("aibuilder.ui.prev_page"), btn -> {
                        scrollOffset = Math.max(0, scrollOffset - maxVisible);
                        init();
                    })
                    .bounds(centerX - buttonWidth - 5, navY, buttonWidth, buttonHeight)
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("aibuilder.ui.next_page"), btn -> {
                        scrollOffset = Math.min(models.size() - maxVisible, scrollOffset + maxVisible);
                        init();
                    })
                    .bounds(centerX + 5, navY, buttonWidth, buttonHeight)
                    .build());
        }

        // 返回按钮
        addRenderableWidget(Button.builder(
                        Component.translatable("aibuilder.ui.back"),
                        btn -> onClose())
                .bounds(10, 10, 60, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Background is extracted by the game separately — do not call extractBackground here
        super.extractRenderState(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int textColor = 0xFFFFFFFF; // ARGB; 0xFFFFFF has alpha 0 and is invisible

        context.centeredText(this.font,
                Component.translatable("aibuilder.select.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                centerX, 15, textColor);

        context.centeredText(this.font,
                Component.translatable("aibuilder.select.count", models.size()).withStyle(ChatFormatting.GRAY),
                centerX, 28, textColor);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}
