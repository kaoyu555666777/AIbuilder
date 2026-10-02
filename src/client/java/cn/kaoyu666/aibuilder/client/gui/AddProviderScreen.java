package cn.kaoyu666.aibuilder.client.gui;

import cn.kaoyu666.aibuilder.client.ai.AIClient;
import cn.kaoyu666.aibuilder.client.config.AIModelConfig;
import cn.kaoyu666.aibuilder.client.config.ModelConfig;
import cn.kaoyu666.aibuilder.client.config.ProviderType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 添加 AI 模型提供商界面
 * 步骤: 选择提供商 → 输入API Key → 获取模型列表 → 选择模型 → 保存
 */
public class AddProviderScreen extends Screen {
    private final Screen parent;

    // 状态
    private ProviderType selectedProvider = null;
    private List<String> fetchedModels = new ArrayList<>();
    private String selectedModel = null;
    private boolean fetching = false;
    private Component statusMessage = Component.empty();
    private ChatFormatting statusFormat = ChatFormatting.WHITE;

    // 输入框
    private EditBox nameField;
    private EditBox customUrlField;
    private EditBox apiKeyField;

    // 输入内容状态（init() 重建控件时保留用户输入）
    private String nameText = "";
    private String customUrlText = "";
    private String apiKeyText = "";

    // 按钮引用
    private Button fetchModelsBtn;
    private Button saveBtn;
    private Button modelSelectBtn;

    public AddProviderScreen(Screen parent) {
        super(Component.translatable("aibuilder.add.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        int centerX = this.width / 2;
        int y = 35;
        int fullWidth = 260;
        int halfWidth = 120;
        int spacing = 24;

        // ---- 第一步: 选择提供商 ----
        // 提供商选择按钮（每行3个）
        ProviderType[] providers = ProviderType.values();
        int cols = 3;
        int btnW = (fullWidth * 2 + 20) / cols - 4;
        int startX = centerX - (fullWidth + 10);

        for (int i = 0; i < providers.length; i++) {
            ProviderType p = providers[i];
            int col = i % cols;
            int row = i / cols;
            int bx = startX + col * (btnW + 4);
            int by = y + row * 22;

            boolean isSelected = (p == selectedProvider);
            Component btnText = Component.literal(p.getDisplayName())
                    .withStyle(isSelected ? ChatFormatting.GREEN : ChatFormatting.WHITE);

            addRenderableWidget(Button.builder(btnText, btn -> {
                        selectedProvider = p;
                        fetchedModels.clear();
                        selectedModel = null;
                        nameText = "";
                        init();
                    })
                    .bounds(bx, by, btnW, 20)
                    .build());
        }

        y += ((providers.length + cols - 1) / cols) * 22 + 15;

        // 自定义URL输入（无默认地址的提供商可见）
        if (selectedProvider != null && selectedProvider.needsCustomUrl()) {
            customUrlField = new EditBox(this.font, centerX - fullWidth / 2, y, fullWidth, 20, Component.literal("API Base URL"));
            customUrlField.setMaxLength(512);
            customUrlField.setValue(this.customUrlText);
            customUrlField.setHint(Component.translatable("aibuilder.add.url_placeholder"));
            customUrlField.setResponder(s -> this.customUrlText = s);
            addRenderableWidget(customUrlField);
            y += spacing + 5;
        }

        // API Key 输入
        apiKeyField = new EditBox(this.font, centerX - fullWidth / 2, y, fullWidth, 20, Component.literal("API Key"));
        apiKeyField.setMaxLength(512);
        apiKeyField.setValue(this.apiKeyText);
        apiKeyField.setHint(Component.translatable("aibuilder.add.apikey_placeholder"));
        apiKeyField.setResponder(s -> this.apiKeyText = s);
        addRenderableWidget(apiKeyField);
        y += spacing;

        // 配置名称
        nameField = new EditBox(this.font, centerX - fullWidth / 2, y, fullWidth, 20, Component.translatable("aibuilder.add.name_field"));
        nameField.setMaxLength(64);
        nameField.setHint(Component.translatable("aibuilder.add.name_placeholder"));
        if (!nameText.isEmpty()) {
            nameField.setValue(nameText);
        } else if (selectedProvider != null) {
            nameField.setValue(selectedProvider.getDisplayName());
        }
        nameField.setResponder(s -> this.nameText = s);
        addRenderableWidget(nameField);
        y += spacing + 5;

        // 获取模型列表按钮
        fetchModelsBtn = Button.builder(
                        Component.translatable(fetching ? "aibuilder.add.fetching" : "aibuilder.add.fetch_models"),
                        btn -> fetchModels())
                .bounds(centerX - fullWidth / 2, y, fullWidth, 20)
                .build();
        fetchModelsBtn.active = selectedProvider != null && !fetching;
        addRenderableWidget(fetchModelsBtn);
        y += spacing + 5;

        // 模型选择按钮
        if (!fetchedModels.isEmpty()) {
            String modelLabel = selectedModel != null ? truncate(selectedModel, 30) : null;
            ChatFormatting modelFormat = selectedModel != null ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
            Component modelBtnText = selectedModel != null
                    ? Component.translatable("aibuilder.add.model", modelLabel).withStyle(modelFormat)
                    : Component.translatable("aibuilder.add.model_none").withStyle(modelFormat);

            modelSelectBtn = Button.builder(
                            modelBtnText,
                            btn -> {
                                if (minecraft != null) {
                                    minecraft.setScreen(new ModelSelectScreen(this, fetchedModels, model -> {
                                        selectedModel = model;
                                        minecraft.setScreen(this);
                                    }));
                                }
                            })
                    .bounds(centerX - fullWidth / 2, y, fullWidth, 20)
                    .build();
            addRenderableWidget(modelSelectBtn);
            y += spacing;
        }

        // 保存按钮
        saveBtn = Button.builder(
                        Component.translatable("aibuilder.add.save").withStyle(ChatFormatting.GREEN),
                        btn -> saveModel())
                .bounds(centerX - halfWidth / 2, y, halfWidth, 20)
                .build();
        saveBtn.active = selectedProvider != null && selectedModel != null;
        addRenderableWidget(saveBtn);

        // 返回按钮
        addRenderableWidget(Button.builder(
                        Component.translatable("aibuilder.ui.back"),
                        btn -> onClose())
                .bounds(10, 10, 60, 20)
                .build());
    }

    private void fetchModels() {
        if (selectedProvider == null) return;

        String apiKey = this.apiKeyText;
        String baseUrl = selectedProvider.needsCustomUrl() ? this.customUrlText : "";

        fetching = true;
        statusMessage = Component.translatable("aibuilder.add.fetching_status");
        statusFormat = ChatFormatting.YELLOW;
        init();

        AIClient.fetchModels(selectedProvider, baseUrl, apiKey)
                .thenAccept(models -> {
                    // UI 状态的修改和界面刷新必须在客户端主线程执行，避免与渲染线程竞争
                    if (minecraft != null) {
                        minecraft.execute(() -> {
                            fetching = false;
                            if (models.isEmpty()) {
                                statusMessage = Component.translatable("aibuilder.add.fetch_empty");
                                statusFormat = ChatFormatting.RED;
                            } else {
                                fetchedModels = new ArrayList<>(models);
                                statusMessage = Component.translatable("aibuilder.add.fetch_success", models.size());
                                statusFormat = ChatFormatting.GREEN;
                                selectedModel = models.get(0);
                            }
                            init();
                        });
                    }
                })
                .exceptionally(e -> {
                    if (minecraft != null) {
                        minecraft.execute(() -> {
                            fetching = false;
                            statusMessage = Component.translatable("aibuilder.add.fetch_failed", e.getMessage());
                            statusFormat = ChatFormatting.RED;
                            init();
                        });
                    }
                    return null;
                });
    }

    private void saveModel() {
        if (selectedProvider == null || selectedModel == null) return;

        String name = !nameText.isEmpty() ? nameText : selectedProvider.getDisplayName();
        String apiKey = this.apiKeyText;
        String baseUrl = selectedProvider.needsCustomUrl() ? this.customUrlText : "";

        ModelConfig config = new ModelConfig(name, selectedProvider, baseUrl, apiKey, selectedModel);
        AIModelConfig.getInstance().addModel(config);

        statusMessage = Component.translatable("aibuilder.add.saved", name);
        statusFormat = ChatFormatting.GREEN;

        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Background is extracted by the game separately — do not call extractBackground here
        super.extractRenderState(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int textColor = 0xFFFFFFFF; // ARGB; 0xFFFFFF has alpha 0 and is invisible

        context.centeredText(this.font,
                Component.translatable("aibuilder.add.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                centerX, 12, textColor);

        if (selectedProvider == null) {
            context.centeredText(this.font,
                    Component.translatable("aibuilder.add.step1").withStyle(ChatFormatting.AQUA),
                    centerX, 25, textColor);
        }

        if (!statusMessage.getString().isEmpty()) {
            context.centeredText(this.font,
                    statusMessage.copy().withStyle(statusFormat),
                    centerX, this.height - 20, textColor);
        }
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
