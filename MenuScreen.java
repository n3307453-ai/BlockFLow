package pl.blockflow;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Menu BlockFLow (prawy Shift).
 * Strony: 0-1 = 10 slotow, 2.. = gotowe schematy (po 5), potem kolejka i tempo, na koncu przesuwanie i obrot.
 */
public final class MenuScreen extends Screen {
    private static int page = 0;

    private final String[] labels = new String[5];

    public MenuScreen() {
        super(Component.literal("BlockFLow"));
    }

    private static int builtinPages() { return (Builtin.names().length + 4) / 5; }
    private static int pages() { return 2 + builtinPages() + 2; }
    private static int queuePage() { return pages() - 2; }
    private static int movePage() { return pages() - 1; }

    private void add(String text, int x, int y, int w, Runnable r) {
        this.addRenderableWidget(Button.builder(Component.literal(text), b -> r.run())
                .bounds(x, y, w, 20).build());
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = 40;
        int row = 26;
        int left = cx - 190;
        int bx = cx - 35;
        String[] names = Builtin.names();

        if (page == movePage()) {
            initMove(left, top);
        } else if (page == queuePage()) {
            initQueue(left, top);
        } else {
            for (int r = 0; r < 5; r++) {
                final int y = top + r * row;
                labels[r] = "";
                if (page < 2) {
                    final int slot = page * 5 + r + 1;
                    Schem.Meta m = Schem.meta(slot);
                    labels[r] = slot + ". " + (m == null
                            ? "- puste -"
                            : m.name() + " " + m.sx() + "x" + m.sy() + "x" + m.sz() + " (" + m.count() + ")");
                    add("Zapisz", bx, y, 50, () -> { Msg.chat(Schem.save(slot)); this.rebuildWidgets(); });
                    add("Wczytaj", bx + 55, y, 54, () -> { Msg.chat(Schem.load(slot)); this.rebuildWidgets(); });
                    add("Usun", bx + 114, y, 40, () -> { Schem.delete(slot); this.rebuildWidgets(); });
                } else {
                    final int idx = (page - 2) * 5 + r;
                    if (idx < names.length) {
                        labels[r] = names[idx];
                        add("Wczytaj", bx + 55, y, 99, () -> { Msg.chat(Builtin.load(idx)); this.rebuildWidgets(); });
                    }
                }
            }
        }

        int by = top + 5 * row + 6;
        add("<", left, by, 30, () -> { page = (page + pages() - 1) % pages(); this.rebuildWidgets(); });
        add(">", left + 35, by, 30, () -> { page = (page + 1) % pages(); this.rebuildWidgets(); });
        add("Postaw na celowniku", left + 70, by, 130, () -> { Actions.place(null); this.onClose(); });
        add("Zamknij", left + 205, by, 60, this::onClose);
    }

    private void initQueue(int left, int top) {
        int w = 124, g = 128;
        add("Dodaj do kolejki", left, top, w, () -> { Actions.queueAdd(); this.rebuildWidgets(); });
        add("Wyczysc kolejke", left + g, top, w, () -> { Actions.queueClear(); this.rebuildWidgets(); });
        add("Tryb: " + (Flow.parallel ? "naraz" : "po kolei"), left + 2 * g, top, w,
                () -> { Actions.queueMode(); this.rebuildWidgets(); });

        int w2 = 90, g2 = 96;
        int y2 = top + 26;
        add("60/s", left, y2, w2, () -> { Actions.tempo(60); this.rebuildWidgets(); });
        add("150/s", left + g2, y2, w2, () -> { Actions.tempo(150); this.rebuildWidgets(); });
        add("300/s", left + 2 * g2, y2, w2, () -> { Actions.tempo(300); this.rebuildWidgets(); });
        add("600/s", left + 3 * g2, y2, w2, () -> { Actions.tempo(600); this.rebuildWidgets(); });

        int y3 = top + 52;
        add("w 10 s", left, y3, w2, () -> { Actions.czas(10); this.rebuildWidgets(); });
        add("w 30 s", left + g2, y3, w2, () -> { Actions.czas(30); this.rebuildWidgets(); });
        add("w 60 s", left + 2 * g2, y3, w2, () -> { Actions.czas(60); this.rebuildWidgets(); });
        add("w 180 s", left + 3 * g2, y3, w2, () -> { Actions.czas(180); this.rebuildWidgets(); });

        int y4 = top + 78;
        add(AutoBuilder.running ? "Pauza budowy" : "START wszystkich", left, y4, 124, () -> {
            Actions.toggleBuild();
            this.rebuildWidgets();
        });
    }

    private void initMove(int left, int top) {
        int w = 90, g = 96;
        add("Obroc 90", left, top, w, () -> { Actions.rotate(90); this.rebuildWidgets(); });
        add("Lustro", left + g, top, w, () -> { Actions.mirror(); this.rebuildWidgets(); });
        add("Krok: " + Actions.step, left + 2 * g, top, w, () -> { Actions.cycleStep(); this.rebuildWidgets(); });
        add("Podpory: " + (AutoBuilder.useTemps ? "TAK" : "nie"), left + 3 * g, top, w,
                () -> { Actions.togglePodpory(); this.rebuildWidgets(); });

        int y2 = top + 26;
        add("Przod", left, y2, w, () -> Actions.nudge("przod", Actions.step));
        add("Tyl", left + g, y2, w, () -> Actions.nudge("tyl", Actions.step));
        add("Lewo", left + 2 * g, y2, w, () -> Actions.nudge("lewo", Actions.step));
        add("Prawo", left + 3 * g, y2, w, () -> Actions.nudge("prawo", Actions.step));

        int y3 = top + 52;
        add("Gora", left, y3, w, () -> Actions.nudge("gora", Actions.step));
        add("Dol", left + g, y3, w, () -> Actions.nudge("dol", Actions.step));
        add(AutoBuilder.running ? "Pauza budowy" : "Start budowy", left + 2 * g, y3, w, () -> {
            Actions.toggleBuild();
            this.rebuildWidgets();
        });
        add("Wyczysc", left + 3 * g, y3, w, () -> { Actions.clear(); this.rebuildWidgets(); });

        int y4 = top + 78;
        add("Warstwy: " + (Flow.layerMode ? "ON" : "off"), left, y4, w, () -> { Actions.layer(null); this.rebuildWidgets(); });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int left = cx - 190;
        String title;
        if (page == movePage()) title = "BlockFLow - przesuwanie i obrot";
        else if (page == queuePage()) title = "BlockFLow - kolejka budynkow i tempo";
        else if (page < 2) title = "BlockFLow - moje budowle (sloty " + (page * 5 + 1) + "-" + (page * 5 + 5) + ")";
        else title = "BlockFLow - gotowe schematy (" + (page - 1) + "/" + builtinPages() + ")";
        graphics.text(this.font, title, left, 16, 0xFFFFFFFF, true);
        graphics.text(this.font, "Strona " + (page + 1) + "/" + pages(), left, 28, 0xFFAAAAAA, true);

        if (page == movePage()) {
            String a = Flow.anchor == null ? "nie postawiono (uzyj: Postaw na celowniku)"
                    : Flow.anchor.getX() + " " + Flow.anchor.getY() + " " + Flow.anchor.getZ()
                    + "  obrot " + Flow.rotationDegrees() + (Flow.mirror.ordinal() != 0 ? " + lustro" : "");
            graphics.text(this.font, "Naroznik: " + a, left, 40 + 104, 0xFFFFFF55, true);
            graphics.text(this.font, "Strzalki = przod/tyl/lewo/prawo, PgUp/PgDn = gora/dol (wzgledem Twojego wzroku)",
                    left, 40 + 116, 0xFF888888, true);
        } else if (page == queuePage()) {
            graphics.text(this.font, "Budynkow w planie: " + Actions.buildings() + "  |  blokow: " + Flow.n
                    + "  |  postawiono: " + Flow.doneCount, left, 40 + 104, 0xFFFFFF55, true);
            graphics.text(this.font, String.format("Tempo: %d blokow/tick = %d/s  |  szac. czas: %.0f s (bez lotu)",
                    AutoBuilder.perTick, AutoBuilder.perTick * 20, Actions.etaSeconds()), left, 40 + 116, 0xFFFFFFFF, true);
            graphics.text(this.font, "Postaw budynek -> Dodaj do kolejki (klawisz K) -> kolejny -> START. Naraz = warstwami we wszystkich.",
                    left, 40 + 128, 0xFF888888, true);
        } else {
            for (int r = 0; r < 5; r++) {
                if (labels[r] == null || labels[r].isEmpty()) continue;
                String t = labels[r].length() > 28 ? labels[r].substring(0, 27) + "~" : labels[r];
                graphics.text(this.font, t, left, 40 + r * 26 + 6, 0xFFFFFFFF, true);
            }
        }
        String st = Flow.clip.length == 0
                ? "Schowek: pusty (zaznacz, skopiuj albo wczytaj)"
                : "Schowek: " + Flow.clip.length + " bl. (" + Flow.sx + "x" + Flow.sy + "x" + Flow.sz + ")"
                + (Flow.anchor != null ? "  | postawiony" : "");
        graphics.text(this.font, st, left, 40 + 5 * 26 + 32, 0xFFFFFF55, true);
    }
}
