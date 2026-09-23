package com.growlink.adapter.web.dto;

import com.growlink.domain.Interes;
import com.growlink.domain.Nivel;
import com.growlink.domain.Perfil;

import java.util.Set;

public record PerfilResponse(String metas, Set<Interes> intereses, Nivel nivel, boolean completo) {
    public static PerfilResponse from(Perfil p) {
        return new PerfilResponse(p.getMetas(), p.getIntereses(), p.getNivel(), p.estaCompleto());
    }
}
