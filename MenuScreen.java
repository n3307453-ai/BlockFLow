package pl.blockflow;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Menu BlockFLow (prawy Shift): 10 slotow na wlasne budowle (2 strony po 5) i gotowe schematy.
 * Zapisz = zapisuje aktualny schowek (po copy). Wczytaj = wrzuca do schowka. Postaw = hologram w miejscu celownika.
 */
public final class MenuScreen extends Screen {
    // 0: sloty 1-5, 1: sloty 6-10, 2..: gotowe schematy (po 5 na strone)
    private static int pages() { return 2 + (Builtin.NAMES.length + 4) / 5; }
    private static int page = 0;

    private final String[] labels = new String[5];

    public MenuScreen() {
        super(Component.literal("BlockFLow"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = 40;
        int row = 26;
        int left = cx - 190;
        int bx = cx - 35;

        for (int r = 0; r < 5; r++) {
            final int y = top + r * row;
            if (page < 2) {
                final int slot = page * 5 + r + 1;
                Schem.Meta m = Schem.meta(slot);
                labels[r] = slot + ". " + (m == null
                        ? "- puste -"
                        : m.name() + " " + m.sx() + "x" + m.sy() + "x" + m.sz() + " (" + m.count() + ")");
                this.addRenderableWidget(Button.builder(Component.literal("Zapisz"), b -> {
                    Msg.chat(Schem.save(slot));
                    this.rebuildWidgets();
                }).bounds(bx, y, 50, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("Wczytaj"), b -> {
                    Msg.chat(Schem.load(slot));
                    this.rebuildWidgets();
                }).bounds(bx + 55, y, 54, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("Usun"), b -> {
                    Schem.delete(slot);
                    this.rebuildWidgets();
                }).bounds(bx + 114, y, 40, 20).build());
            } else if ((page - 2) * 5 + r < Builtin.NAMES.length) {
                final int idx = (page - 2) * 5 + r;
                labels[r] = Builtin.NAMES[idx];
                this.addRenderableWidget(Button.builder(Component.literal("Wczytaj"), b -> {
                    Msg.chat(Builtin.load(idx));
                    this.rebuildWidgets();
                }).bounds(bx + 55, y, 54, 20).build());
            } else {
                labels[r] = "";
            }
        }

        int by = top + 5 * row + 6;
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = (page + pages() - 1) % pages();
            this.rebuildWidgets();
        }).bounds(left, by, 30, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page = (page + 1) % pages();
            this.rebuildWidgets();
        }).bounds(left + 35, by, 30, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Postaw na celowniku"), b -> {
            Actions.place(null);
            this.onClose();
        }).bounds(left + 70, by, 130, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Zamknij"), b -> this.onClose())
                .bounds(left + 205, by, 60, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int left = cx - 190;
        String title = page < 2 ? "BlockFLow - moje budowle (sloty " + (page * 5 + 1) + "-" + (page * 5 + 5) + ")"
                : "BlockFLow - gotowe schematy (" + (page - 1) + "/" + (pages() - 2) + ")";
        graphics.text(this.font, title, left, 16, 0xFFFFFFFF, true);
        graphics.text(this.font, "Strona " + (page + 1) + "/" + pages(), left, 28, 0xFFAAAAAA, true);
        for (int r = 0; r < 5; r++) {
            if (labels[r] == null || labels[r].isEmpty()) continue;
            String t = labels[r].length() > 28 ? labels[r].substring(0, 27) + "~" : labels[r];
            graphics.text(this.font, t, left, 40 + r * 26 + 6, 0xFFFFFFFF, true);
        }
        String st = Flow.clip.length == 0
                ? "Schowek: pusty (zaznacz, skopiuj albo wczytaj)"
                : "Schowek: " + Flow.clip.length + " bl. (" + Flow.sx + "x" + Flow.sy + "x" + Flow.sz + ")"
                + (Flow.anchor != null ? "  | postawiony" : "");
        graphics.text(this.font, st, left, 40 + 5 * 26 + 32, 0xFFFFFF55, true);
        graphics.text(this.font, "Zapisz: zapisuje schowek (po copy) | Postaw: hologram tam, gdzie patrzysz",
                left, 40 + 5 * 26 + 44, 0xFF888888, true);
    }
}
