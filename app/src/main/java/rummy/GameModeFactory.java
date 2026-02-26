package rummy;

// Simple factory
public class GameModeFactory {

    private GameModeFactory() {}

    public static GameMode create(String mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Game mode cannot be null.");
        }

        return switch (mode.toLowerCase()) {
            case "classic" -> new ClassicRummy();
            case "gin" -> new GinRummy();
            default -> {
                System.out.println("Unknown mode: " + mode + ". Defaulting to Classic Rummy.");
                yield new ClassicRummy();
            }
        };
    }

}
