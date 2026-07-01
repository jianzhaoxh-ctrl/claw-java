package com.openclaw.desktop.ui.chat;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * 多 Agent 切换器 — 顶栏中的 Agent 选择下拉框。
 *
 * <p>允许用户在多个预设 Agent 配置间切换（如：默认助手、代码专家、翻译专家等）。
 * 切换时通过回调通知 ChatView 重建 Agent。
 */
public class AgentSwitcher {

    private static final Logger log = LoggerFactory.getLogger(AgentSwitcher.class);

    private final ObservableList<AgentOption> agents = FXCollections.observableArrayList();
    private final ComboBox<AgentOption> comboBox;
    private Consumer<AgentOption> onSwitch;
    private AgentOption current;

    public AgentSwitcher() {
        comboBox = new ComboBox<>(agents);
        comboBox.setPromptText("选择 Agent");
        comboBox.setPrefWidth(180);
        comboBox.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(AgentOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        comboBox.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(AgentOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        comboBox.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null && !n.equals(current)) {
                current = n;
                log.info("Agent switched to: {} ({})", n.displayName(), n.id());
                if (onSwitch != null) onSwitch.accept(n);
            }
        });
    }

    /** 控件节点。 */
    public ComboBox<AgentOption> node() {
        return comboBox;
    }

    /** 设置切换回调。 */
    public void setOnSwitch(Consumer<AgentOption> handler) {
        this.onSwitch = handler;
    }

    /** 添加 Agent 选项。 */
    public void addAgent(AgentOption option) {
        agents.add(option);
        if (agents.size() == 1) {
            comboBox.getSelectionModel().select(0);
            current = option;
        }
    }

    /** 批量添加。 */
    public void addAgents(List<AgentOption> options) {
        agents.addAll(options);
        if (!options.isEmpty() && current == null) {
            comboBox.getSelectionModel().select(0);
            current = agents.get(0);
        }
    }

    /** 当前选中的 Agent。 */
    public AgentOption current() {
        return current;
    }

    /** 切换到指定 Agent。 */
    public void switchTo(String agentId) {
        for (var a : agents) {
            if (a.id().equals(agentId)) {
                comboBox.getSelectionModel().select(a);
                return;
            }
        }
        log.warn("Agent not found: {}", agentId);
    }

    /** 所有 Agent 选项。 */
    public List<AgentOption> agents() {
        return List.copyOf(agents);
    }

    /** Agent 选项记录。 */
    public record AgentOption(
        String id,
        String displayName,
        String modelId,
        String systemPrompt,
        double temperature
    ) {
        public AgentOption(String id, String displayName, String modelId) {
            this(id, displayName, modelId, null, 0.7);
        }
    }
}
