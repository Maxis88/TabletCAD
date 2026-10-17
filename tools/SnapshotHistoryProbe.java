import java.util.*;
import pl.tabletcad.app.SnapshotHistory;

public final class SnapshotHistoryProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }
    public static void main(String[] args) throws Exception {
        Deque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
        undo.push("older"); undo.push("previous");
        String[] active = {"current"};
        SnapshotHistory.move(undo, redo, active[0], value -> active[0] = value);
        check(active[0].equals("previous") && undo.peek().equals("older")
            && redo.peek().equals("current"), "successful undo moves exactly one entry");
        SnapshotHistory.move(redo, undo, active[0], value -> active[0] = value);
        check(active[0].equals("current") && undo.peek().equals("previous")
            && redo.isEmpty(), "redo restores original ordering");
        for (String reason : new String[]{"missing STEP source", "invalid geometry", "save failure"}) {
            List<String> before = new ArrayList<>(undo);
            boolean failed = false;
            try { SnapshotHistory.move(undo, redo, active[0], value -> { throw new Exception(reason); }); }
            catch (Exception expected) { failed = true; }
            check(failed && before.equals(new ArrayList<>(undo)) && redo.isEmpty()
                && active[0].equals("current"), reason + " preserves history and current state");
        }
        SnapshotHistory.move(redo, undo, active[0], value -> { throw new AssertionError("empty history called restorer"); });
        check(undo.size() == 2, "empty history is a no-op");
        System.out.println("ALL SNAPSHOT HISTORY TESTS PASSED");
    }
}
