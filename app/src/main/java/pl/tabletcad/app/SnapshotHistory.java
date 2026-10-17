package pl.tabletcad.app;

import java.util.Deque;

/** Moves a history entry only after its document has been restored successfully. */
public final class SnapshotHistory {
    private SnapshotHistory() {}

    public interface Restorer { void restore(String snapshot) throws Exception; }

    public static void move(Deque<String> source, Deque<String> destination,
            String current, Restorer restorer) throws Exception {
        if (source.isEmpty()) return;
        restorer.restore(source.peek());
        source.pop();
        destination.push(current);
    }
}
