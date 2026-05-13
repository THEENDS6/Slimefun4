package cc.theends6.sfx.internal.electric;

public enum SfxElectricMachineMenuStyle {
    STANDARD(45),
    SIMPLE_IO(27);

    private final int inventorySize;

    SfxElectricMachineMenuStyle(int inventorySize) {
        this.inventorySize = inventorySize;
    }

    int inventorySize() {
        return inventorySize;
    }
}
