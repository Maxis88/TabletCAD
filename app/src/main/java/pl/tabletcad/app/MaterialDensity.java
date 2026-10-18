package pl.tabletcad.app;

/** Homogeneous density for the whole document; geometry dimensions are in millimetres. */
public final class MaterialDensity {
    private MaterialDensity() {}

    /** Zero means unspecified. A specified density must be finite and positive, in kg/m³. */
    public static void validate(double density) {
        if (!Double.isFinite(density) || density < 0)
            throw new IllegalArgumentException("Gęstość musi być dodatnią, skończoną liczbą w kg/m³");
    }

    /** Accept either Polish comma or decimal point. Blank input removes the assignment. */
    public static double parse(String text) {
        String value = text.trim();
        if (value.isEmpty()) return 0;
        if (!value.matches("(?:[0-9]+(?:[.,][0-9]+)?|[.,][0-9]+)"))
            throw new IllegalArgumentException("Podaj gęstość w kg/m³, bez separatorów tysięcy");
        double density = Double.parseDouble(value.replace(',', '.'));
        validate(density);
        if (density == 0) throw new IllegalArgumentException("Gęstość musi być większa od zera; puste pole usuwa przypisanie");
        return density;
    }

    public static double massKg(double volumeMm3, double densityKgM3) {
        validate(densityKgM3);
        if (densityKgM3 == 0) throw new IllegalArgumentException("Nie przypisano gęstości materiału");
        if (!Double.isFinite(volumeMm3) || volumeMm3 < 0)
            throw new IllegalArgumentException("Niepoprawna objętość bryły");
        double mass = volumeMm3 * (densityKgM3 / 1_000_000_000.0);
        if (!Double.isFinite(mass) || (volumeMm3 > 0 && mass == 0))
            throw new IllegalArgumentException("Masa poza zakresem obliczeń");
        return mass;
    }
}
