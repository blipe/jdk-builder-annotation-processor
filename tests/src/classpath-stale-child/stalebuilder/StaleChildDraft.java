package stalebuilder;

public final class StaleChildDraft {
    private StaleChildDraft() {
    }

    public static StaleChildDraft builder() {
        return new StaleChildDraft();
    }

    public StaleChild finish() {
        return new StaleChild("stale");
    }
}
