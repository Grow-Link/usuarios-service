package com.growlink.adapter.persistence;

import com.growlink.domain.Usuario;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Con esto se reconoce a quien vuelve a entrar con Microsoft: el sub del token es la llave
    // (columna unica, ver Usuario.proveedorSub), no el correo, que la persona puede cambiar.
    Optional<Usuario> findByProveedorSub(String proveedorSub);

    @Query("SELECT u FROM Usuario u WHERE u.id <> :excluir AND "
            + "(LOWER(u.nombre) LIKE CONCAT('%', :q, '%') OR LOWER(u.cargo) LIKE CONCAT('%', :q, '%')) "
            + "ORDER BY u.nombre")
    List<Usuario> buscar(@Param("q") String q, @Param("excluir") Long excluir, org.springframework.data.domain.Pageable limite);

    default List<Usuario> buscar(String q, Long excluir) {
        return buscar(q, excluir, PageRequest.of(0, 10));
    }
}
