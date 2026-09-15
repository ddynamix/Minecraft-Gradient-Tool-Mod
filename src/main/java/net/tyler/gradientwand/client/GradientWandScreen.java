package net.tyler.gradientwand.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.tyler.gradientwand.core.WandSettings;
import net.tyler.gradientwand.network.WandRedoPacket;
import net.tyler.gradientwand.network.WandSettingsPacket;
import net.tyler.gradientwand.network.WandUndoPacket;

public class GradientWandScreen extends Screen {

    private static final int WIDGET_WIDTH = 200;
    private static final int WIDGET_HEIGHT = 20;
    private static final int SPACING = 24;
    private static final int WIDTH_BOX = 50;

    // Shape, gradient axis, easing, grain, dither, blend
    private static final int SETTING_ROWS = 6;

    // Leaves room for the title above the first row on the smallest window Minecraft allows
    private static final int TOP_MARGIN = 24;

    private WandSettings settings;

    // The wand's own ribbon limit, so the box cannot be typed past what this tier allows
    private final int maxWidth;

    // Where the widgets start, so render() can put the title directly above them
    private int contentTop;

    public GradientWandScreen(WandSettings settings, int maxWidth) {
        super(Text.literal("Gradient Wand"));
        this.settings = settings;
        this.maxWidth = maxWidth;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - WIDGET_WIDTH / 2;

        // Laid out from the middle rather than from a fixed quarter of the screen. With six
        // setting rows the old height/4 origin pushed the Done button off the bottom of a 240
        // pixel window, which is the smallest Minecraft will run at.
        int contentHeight = SPACING * SETTING_ROWS + 8 + SPACING + WIDGET_HEIGHT;
        int y = Math.max(TOP_MARGIN, (this.height - contentHeight) / 2);

        this.contentTop = y;

        boolean ribbon = settings.mode() == WandSettings.Mode.RIBBON;
        int modeWidth = ribbon ? WIDGET_WIDTH - WIDTH_BOX - 4 : WIDGET_WIDTH;

        addDrawableChild(CyclingButtonWidget.<WandSettings.Mode>builder(GradientWandScreen::label)
                .values(WandSettings.Mode.values())
                .initially(settings.mode())
                .build(x, y, modeWidth, WIDGET_HEIGHT, Text.literal("Shape"),
                        (button, value) -> {
                            apply(settings.withMode(value));

                            // The width box only belongs to ribbons, so lay the screen out again
                            clearAndInit();
                        }));

        if (ribbon) {
            TextFieldWidget widthField = new TextFieldWidget(this.textRenderer,
                    x + WIDGET_WIDTH - WIDTH_BOX, y, WIDTH_BOX, WIDGET_HEIGHT, Text.literal("Width"));

            widthField.setMaxLength(String.valueOf(maxWidth).length());
            widthField.setTextPredicate(text -> text.chars().allMatch(Character::isDigit));

            // Set the text before attaching the listener, or opening the screen sends a packet
            widthField.setText(String.valueOf(settings.width()));
            widthField.setChangedListener(text -> {
                if (!text.isEmpty()) {
                    apply(settings.withWidth(Math.min(Integer.parseInt(text), maxWidth)));
                }
            });

            addDrawableChild(widthField);
        }

        addDrawableChild(CyclingButtonWidget.<WandSettings.GradientAxis>builder(GradientWandScreen::label)
                .values(WandSettings.GradientAxis.values())
                .initially(settings.axis())
                .build(x, y + SPACING, WIDGET_WIDTH, WIDGET_HEIGHT, Text.literal("Gradient axis"),
                        (button, value) -> apply(settings.withAxis(value))));

        // Sits next to the gradient axis: both are about how the blend is spread along the run
        addDrawableChild(CyclingButtonWidget.<WandSettings.Easing>builder(GradientWandScreen::label)
                .values(WandSettings.Easing.values())
                .initially(settings.easing())
                .build(x, y + SPACING * 2, WIDGET_WIDTH, WIDGET_HEIGHT, Text.literal("Easing"),
                        (button, value) -> apply(settings.withEasing(value))));

        addDrawableChild(CyclingButtonWidget.<WandSettings.Grain>builder(GradientWandScreen::label)
                .values(WandSettings.Grain.values())
                .initially(settings.grain())
                .build(x, y + SPACING * 3, WIDGET_WIDTH, WIDGET_HEIGHT, Text.literal("Grain"),
                        (button, value) -> apply(settings.withGrain(value))));

        addDrawableChild(CyclingButtonWidget.<WandSettings.Dither>builder(GradientWandScreen::label)
                .values(WandSettings.Dither.values())
                .initially(settings.dither())
                .build(x, y + SPACING * 4, WIDGET_WIDTH, WIDGET_HEIGHT, Text.literal("Dither"),
                        (button, value) -> apply(settings.withDither(value))));

        addDrawableChild(new JitterSlider(x, y + SPACING * 5, WIDGET_WIDTH, WIDGET_HEIGHT, settings.jitter()));

        int half = (WIDGET_WIDTH - 4) / 2;
        int buttonY = y + SPACING * SETTING_ROWS + 8;

        addDrawableChild(ButtonWidget.builder(Text.literal("Undo"),
                        button -> ClientPlayNetworking.send(new WandUndoPacket()))
                .dimensions(x, buttonY, half, WIDGET_HEIGHT)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Redo"),
                        button -> ClientPlayNetworking.send(new WandRedoPacket()))
                .dimensions(x + half + 4, buttonY, half, WIDGET_HEIGHT)
                .build());

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
                .dimensions(x, buttonY + SPACING, WIDGET_WIDTH, WIDGET_HEIGHT)
                .build());
    }

    // Enum names become button labels, so EAST_WEST has to come out as "East/West"
    private static Text label(Enum<?> value) {
        String[] parts = value.name().toLowerCase().split("_");

        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].substring(0, 1).toUpperCase() + parts[i].substring(1);
        }

        return Text.literal(String.join("/", parts));
    }

    // Keep a local copy so the screen stays responsive, and tell the server what changed
    private void apply(WandSettings updated) {
        settings = updated;

        ClientPlayNetworking.send(new WandSettingsPacket(updated.mode(), updated.axis(),
                updated.dither(), updated.grain(), updated.easing(), updated.jitter(), updated.width()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1.21 passes the mouse position and tick delta through to the background renderer
        //? if <1.21 {
        /*renderBackground(context);
        *///?} else {
        renderBackground(context, mouseX, mouseY, delta);
        //?}

        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, contentTop - 16, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    // Leave the world running so the preview keeps updating behind the menu
    @Override
    public boolean shouldPause() {
        return false;
    }

    private class JitterSlider extends SliderWidget {

        JitterSlider(int x, int y, int width, int height, float jitter) {
            super(x, y, width, height, Text.empty(), jitter);

            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(String.format("Blend: %.2f", this.value)));
        }

        @Override
        protected void applyValue() {
            apply(settings.withJitter((float) this.value));
        }
    }
}
