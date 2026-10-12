package com.growlink.domain;

// una planta pasa por estas 4 etapas segun cuantos riegos lleva: 0, 1, 2 y 3 (de ahi no sube mas)
public enum Etapa {
    SEMILLA,
    BROTE,
    PLANTA,
    FLORECIDA;

    public static Etapa deRiegos(int riegos) {
        return switch (riegos) {
            case 0 -> SEMILLA;
            case 1 -> BROTE;
            case 2 -> PLANTA;
            default -> FLORECIDA;
        };
    }
}
