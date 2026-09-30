package faygolover.zoneartifacts.client.pda;

import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.network.PdaApplyPacket;
import faygolover.zoneartifacts.pda.PdaParam;
import faygolover.zoneartifacts.pda.PdaTarget;
import faygolover.zoneartifacts.pda.PdaView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The KPK window for one anomaly: a slider per setting (with − / + for exact steps; the standard
 * value in its tooltip), the four switches, moving it a block at a time, and Apply / Defaults /
 * Done / Cancel. Nothing changes until it is applied; the server clamps everything and sends the
 * window back fresh ({@link #show}).
 */
public class PdaScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int ROW_H = 22;

    private PdaView view;
    private final Map<String, Double> values = new LinkedHashMap<>();
    private int switches;
    private final Map<String, ValueSlider> sliders = new HashMap<>();
    /** The delete button was pressed once: the next press deletes. */
    private boolean confirmDelete;

    private PdaScreen(PdaView view) {
        super(view.title());
        take(view);
    }

    /** A window's data from the server: refresh the open window for it, or open a new one. */
    public static void show(PdaView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PdaScreen open && sameThing(open.view.target(), view.target())) {
            open.take(view);
            open.rebuildWidgets();
            return;
        }
        mc.setScreen(new PdaScreen(view));
    }

    /** The same anomaly (a moved zone keeps its type; its position is what changed). */
    private static boolean sameThing(PdaTarget a, PdaTarget b) {
        if (a.kind() != b.kind()) return false;
        if (a.kind() == PdaTarget.ZONE) return a.typeId() != null && a.typeId().equals(b.typeId());
        if (a.kind() == PdaTarget.BLOCK) return a.pos().equals(b.pos());
        return a.id() == b.id();
    }

    private void take(PdaView view) {
        this.view = view;
        values.clear();
        for (PdaParam p : view.params()) values.put(p.id(), p.value());
        switches = view.switches();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        sliders.clear();
        int left = (width - PANEL_W) / 2;
        int top = Math.max(8, (height - panelHeight()) / 2) + 22;

        int y = top;
        for (PdaParam p : view.params()) {
            int rowY = y;
            addRenderableWidget(Button.builder(Component.literal("−"), b -> nudge(p, -1)).bounds(left, rowY, 18, 20).build());
            ValueSlider slider = new ValueSlider(left + 20, rowY, 196, 20, p);
            slider.setTooltip(Tooltip.create(Component.translatable("pda.fl_zone_arts.standard", format(p, p.standard()))));
            sliders.put(p.id(), slider);
            addRenderableWidget(slider);
            addRenderableWidget(Button.builder(Component.literal("+"), b -> nudge(p, 1)).bounds(left + 218, rowY, 18, 20).build());
            y += ROW_H;
        }
        int bottom = y;

        int rx = left + 246;
        int ry = top;
        if (switches >= 0) {
            String[] keys = {"pda.fl_zone_arts.enabled", "pda.fl_zone_arts.harmful", "pda.fl_zone_arts.audible", "pda.fl_zone_arts.visible",
                    "pda.fl_zone_arts.targets_gm"};
            for (int i = 0; i < keys.length; i++) {
                int bit = 1 << i;
                if ((view.switchMask() & bit) == 0) continue;
                addRenderableWidget(new Switch(rx, ry, 94, 20, Component.translatable(keys[i]), (switches & bit) != 0, bit));
                ry += ROW_H;
            }
        }
        if (view.movable()) {
            ry += 12;
            String[] axes = {"X", "Y", "Z"};
            for (int i = 0; i < 3; i++) {
                int axis = i;
                addRenderableWidget(Button.builder(Component.literal(axes[i] + " −"), b -> move(axis, -1)).bounds(rx, ry, 46, 20).build());
                addRenderableWidget(Button.builder(Component.literal(axes[i] + " +"), b -> move(axis, 1)).bounds(rx + 48, ry, 46, 20).build());
                ry += ROW_H;
            }
        }
        ry += 12;
        Component del = confirmDelete ? Component.translatable("pda.fl_zone_arts.delete_sure").withStyle(net.minecraft.ChatFormatting.RED)
                : Component.translatable("pda.fl_zone_arts.delete");
        addRenderableWidget(Button.builder(del, b -> {
            if (!confirmDelete) {
                confirmDelete = true;
                rebuildWidgets();
                return;
            }
            ModNetwork.CHANNEL.sendToServer(new PdaApplyPacket(view.target(), new HashMap<>(), -1, 0, 0, 0, false, true));
            onClose();
        }).bounds(rx, ry, 94, 20).build());
        ry += ROW_H;
        bottom = Math.max(bottom, ry) + 8;

        int bw = (PANEL_W - 12) / 4;
        addRenderableWidget(Button.builder(Component.translatable("pda.fl_zone_arts.apply"), b -> apply(0, 0, 0, false))
                .bounds(left, bottom, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("pda.fl_zone_arts.defaults"), b -> apply(0, 0, 0, true))
                .bounds(left + bw + 4, bottom, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("pda.fl_zone_arts.done"), b -> {
            apply(0, 0, 0, false);
            onClose();
        }).bounds(left + (bw + 4) * 2, bottom, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("pda.fl_zone_arts.cancel"), b -> onClose())
                .bounds(left + (bw + 4) * 3, bottom, bw, 20).build());
    }

    private int panelHeight() {
        int left = view.params().size() * ROW_H;
        int right = (view.switches() >= 0 ? Integer.bitCount(view.switchMask()) * ROW_H : 0) + (view.movable() ? 12 + 3 * ROW_H : 0)
                + 12 + ROW_H;
        return 22 + Math.max(left, right) + 8 + 20;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int left = (width - PANEL_W) / 2;
        int top = Math.max(8, (height - panelHeight()) / 2);
        g.fill(left - 8, top - 6, left + PANEL_W + 8, top + panelHeight() + 6, 0xC0101418);
        g.drawString(font, title, left, top, 0xFFE8F0FF);
        if (view.movable()) {
            String at = view.target().pos().getX() + " " + view.target().pos().getY() + " " + view.target().pos().getZ();
            Component where = Component.translatable("pda.fl_zone_arts.position", at);
            g.drawString(font, where, left + PANEL_W - font.width(where), top, 0xFF9AA8BC);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private void nudge(PdaParam p, int dir) {
        double v = p.snap(values.getOrDefault(p.id(), p.value()) + dir * p.step());
        values.put(p.id(), v);
        ValueSlider slider = sliders.get(p.id());
        if (slider != null) slider.set(v);
    }

    private void move(int axis, int dir) {
        apply(axis == 0 ? dir : 0, axis == 1 ? dir : 0, axis == 2 ? dir : 0, false);
    }

    private void apply(int dx, int dy, int dz, boolean reset) {
        ModNetwork.CHANNEL.sendToServer(new PdaApplyPacket(view.target(), new HashMap<>(values),
                view.switches() >= 0 ? switches : -1, dx, dy, dz, reset, false));
    }

    static String format(PdaParam p, double v) {
        if (p.step() >= 1.0 && v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, p.step() < 0.1 ? "%.2f" : "%.1f", v);
    }

    /** A setting's slider: min..max, on its steps. */
    private final class ValueSlider extends AbstractSliderButton {
        private final PdaParam param;

        ValueSlider(int x, int y, int w, int h, PdaParam param) {
            super(x, y, w, h, Component.empty(), fraction(param, values.getOrDefault(param.id(), param.value())));
            this.param = param;
            updateMessage();
        }

        void set(double v) {
            this.value = fraction(param, v);
            updateMessage();
        }

        private double current() {
            return param.snap(param.min() + value * (param.max() - param.min()));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(param.label()).append(": " + format(param, current())));
        }

        @Override
        protected void applyValue() {
            values.put(param.id(), current());
        }
    }

    private static double fraction(PdaParam p, double v) {
        return p.max() > p.min() ? Mth.clamp((v - p.min()) / (p.max() - p.min()), 0.0, 1.0) : 0.0;
    }

    /** One of the four switches. */
    private final class Switch extends Checkbox {
        private final int bit;

        Switch(int x, int y, int w, int h, Component label, boolean on, int bit) {
            super(x, y, w, h, label, on);
            this.bit = bit;
        }

        @Override
        public void onPress() {
            super.onPress();
            switches = selected() ? (switches | bit) : (switches & ~bit);
        }
    }
}
