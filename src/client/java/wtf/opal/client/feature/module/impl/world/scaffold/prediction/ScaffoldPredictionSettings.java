package wtf.opal.client.feature.module.impl.world.scaffold.prediction;

import wtf.opal.client.feature.helper.impl.player.rotation.RotationProperty;
import wtf.opal.client.feature.helper.impl.player.rotation.model.IRotationModel;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;

public final class ScaffoldPredictionSettings {
    private final RotationProperty rotationProperty;
    private final ModeProperty<RotationMode> rotationMode;
    private final NumberProperty rotationSpeed;
    private final ModeProperty<SprintMode> sprintMode;
    private final NumberProperty groundMotion;
    private final NumberProperty airMotion;
    private final NumberProperty speedMotion;
    private final ModeProperty<TowerMode> tower;
    private final ModeProperty<KeepYMode> keepY;
    private final BooleanProperty keepYonPress;
    private final BooleanProperty disableWhileJumpActive;
    private final BooleanProperty multiplace;
    private final BooleanProperty safeWalk;
    private final BooleanProperty swing;
    private final BooleanProperty itemSpoof;
    private final BooleanProperty blockCounter;

    public ScaffoldPredictionSettings(final ScaffoldPrediction module) {
        this.rotationProperty = new RotationProperty(InstantRotationModel.INSTANCE);
        this.rotationMode = new ModeProperty<>("Rotations", RotationMode.DEFAULT);
        this.rotationSpeed = new NumberProperty("Rotation speed", 1.0d, 0.0d, 2.0d, 0.1d);
        this.sprintMode = new ModeProperty<>("Sprint", SprintMode.VANILLA);
        this.groundMotion = new NumberProperty("Ground motion", 1.0d, 0.0d, 1.0d, 0.01d);
        this.airMotion = new NumberProperty("Air motion", 1.0d, 0.0d, 1.0d, 0.01d);
        this.speedMotion = new NumberProperty("Speed motion", 1.0d, 0.0d, 1.0d, 0.01d);
        this.tower = new ModeProperty<>("Tower", TowerMode.VANILLA);
        this.keepY = new ModeProperty<>("Keep Y", KeepYMode.VANILLA);
        this.keepYonPress = new BooleanProperty("Keep Y on press", false).hideIf(() -> this.keepY.getValue() == KeepYMode.NONE);
        this.disableWhileJumpActive = new BooleanProperty("No keep Y on jump potion", false).hideIf(() -> this.keepY.getValue() == KeepYMode.NONE);
        this.multiplace = new BooleanProperty("Multi place", true);
        this.safeWalk = new BooleanProperty("Safe walk", true);
        this.swing = new BooleanProperty("Swing", true);
        this.itemSpoof = new BooleanProperty("Item spoof", false);
        this.blockCounter = new BooleanProperty("Block counter", true);

        module.addProperties(rotationProperty.get(), rotationMode, rotationSpeed, sprintMode, groundMotion, airMotion, speedMotion,
                           tower, keepY, keepYonPress, disableWhileJumpActive, multiplace, safeWalk, 
                           swing, itemSpoof, blockCounter);
    }

    public RotationMode getRotationMode() {
        return rotationMode.getValue();
    }

    public float getRotationSpeed() {
        return rotationSpeed.getValue().floatValue();
    }

    public SprintMode getSprintMode() {
        return sprintMode.getValue();
    }

    public float getGroundMotion() {
        return groundMotion.getValue().floatValue();
    }

    public float getAirMotion() {
        return airMotion.getValue().floatValue();
    }

    public float getSpeedMotion() {
        return speedMotion.getValue().floatValue();
    }

    public TowerMode getTowerMode() {
        return tower.getValue();
    }

    public KeepYMode getKeepYMode() {
        return keepY.getValue();
    }

    public boolean isKeepYonPress() {
        return keepYonPress.getValue();
    }

    public boolean isDisableWhileJumpActive() {
        return disableWhileJumpActive.getValue();
    }

    public boolean isMultiplace() {
        return multiplace.getValue();
    }

    public boolean isSafeWalk() {
        return safeWalk.getValue();
    }

    public boolean isSwing() {
        return swing.getValue();
    }

    public boolean isItemSpoof() {
        return itemSpoof.getValue();
    }

    public boolean isBlockCounter() {
        return blockCounter.getValue();
    }

    public IRotationModel createRotationModel() {
        return rotationProperty.createModel();
    }

    public enum RotationMode {
        NONE("None"),
        DEFAULT("Default"),
        BACKWARDS("Backwards"),
        SIDEWAYS("Sideways");

        private final String name;

        RotationMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum SprintMode {
        NONE("None"),
        VANILLA("Vanilla");

        private final String name;

        SprintMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum TowerMode {
        NONE("None"),
        VANILLA("Vanilla"),
        EXTRA("Extra"),
        TELLY("Telly");

        private final String name;

        TowerMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum KeepYMode {
        NONE("None"),
        VANILLA("Vanilla"),
        EXTRA("Extra"),
        TELLY("Telly");

        private final String name;

        KeepYMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}