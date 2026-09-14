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
import net.tyler.gradientwand.item.custom.WandSettings;
import net.tyler.gradientwand.network.WandRedoPacket;
import net.tyler.gradientwand.network.WandSettingsPacket;
import net.tyler.gradientwand.network.WandUndoPacket;

public class GradientWandScreen extends Screen {

    private static final int WIDGET_WIDTH = 200;
    private static final int WIDGET_HEIGHT = 20;
    private static final int SPACING = 24;
    private static final int WIDTH_BOX = 50;

    private WandSettings settings;

    public GradientWandScreen(WandSettings settings) {
        super(Text.literal("Gradient Wand"));
        this.settings = settings;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - WIDGET_WIDTH / 2;
        int y = this.height / 4;

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

            widthField.setMaxLength(2);
            widthField.setTextPredicate(text -> text.chars().allMatch(Character::isDigit));

            // Set the text before attaching the listener, or opening the screen sends a packet
            widthField.setText(String.valueOf(settings.width()));
            widthField.setChangedListener(text -> {
                if (!text.isEmpty()) {
                    apply(settings.withWidth(Integer.parseInt(text)));
                }
            });

            addDrawableChild(widthField);
        }

        addDrawableChild(CyclingButtonWidget.<WandSettings.GradientAxis>builder(GradientWandScreen::label)
                .values(WandSettings.GradientAxis.values())
                .initially(settings.axis())
                .build(x, y + SPACING, WIDGET_WIDTH, WIDGET_HEIGHT, Text.literal("Gradient axis"),
                        (button, value) -> apply(settings.withAxis(value))));

        addDrawableChild(CyclingButtonWidget.<WandSettings.Dither>builder(GradientWandScreen::label)
                .values(WandSettings.Dither.values())
                .initially(settings.dither())
                .build(x, y + SPACING * 2, WIDGET_WIDTH, WIDGET_HEIGHT, Text.literal("Dither"),
                        (button, value) -> apply(settings.withDither(value))));

        addDrawableChild(new JitterSlider(x, y + SPACING * 3, WIDGET_WIDTH, WIDGET_HEIGHT, settings.jitter()));

        int half = (WIDGET_WIDTH - 4) / 2;
        int buttonY = y + SPACING * 4 + 8;

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

    private static Text label(Enum<?> value) {
        String name = value.name().toLowerCase();

        return Text.literal(name.substring(0, 1).toUpperCase() + name.substring(1));
    }

    // Keep a local copy so the screen stays responsive, and tell the server what changed
    private void apply(WandSettings updated) {
        settings = updated;

        ClientPlayNetworking.send(new WandSettingsPacket(updated.mode(), updated.axis(),
                updated.dither(), updated.jitter(), updated.width()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, this.height / 4 - 20, 0xFFFFFF);

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