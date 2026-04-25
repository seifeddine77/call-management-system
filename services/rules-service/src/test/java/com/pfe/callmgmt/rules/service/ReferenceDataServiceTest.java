package com.pfe.callmgmt.rules.service;

import com.pfe.callmgmt.rules.dto.ReferenceDataResponse;
import com.pfe.callmgmt.rules.entity.ReferenceItem;
import com.pfe.callmgmt.rules.repository.ReferenceItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du service de données de référence (CDC 4.B)
 * Utilise Mockito pour mocker le ReferenceItemRepository.
 */
@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceTest {

    @Mock
    private ReferenceItemRepository referenceItemRepository;

    @InjectMocks
    private ReferenceDataService service;

    @BeforeEach
    void setupMocks() {
        // Seed mock data pour les 3 catégories
        when(referenceItemRepository.findByCategoryAndActifTrueOrderByDisplayOrderAsc("TYPE_PIECE"))
                .thenReturn(List.of(
                        ReferenceItem.builder().code("MECANIQUE").label("Mécanique").build(),
                        ReferenceItem.builder().code("ELECTRIQUE").label("Électrique").build(),
                        ReferenceItem.builder().code("CARROSSERIE").label("Carrosserie").build(),
                        ReferenceItem.builder().code("ELECTRONIQUE").label("Électronique").build(),
                        ReferenceItem.builder().code("PNEUMATIQUE").label("Pneumatique").build(),
                        ReferenceItem.builder().code("AUTRE").label("Autre").build()));

        when(referenceItemRepository.findByCategoryAndActifTrueOrderByDisplayOrderAsc("CATEGORIE_CLIENT"))
                .thenReturn(List.of(
                        ReferenceItem.builder().code("STANDARD").label("Standard").build(),
                        ReferenceItem.builder().code("PREMIUM").label("Premium").build(),
                        ReferenceItem.builder().code("VIP").label("VIP").build(),
                        ReferenceItem.builder().code("PROFESSIONNEL").label("Professionnel").build()));

        when(referenceItemRepository.findByCategoryAndActifTrueOrderByDisplayOrderAsc("TYPE_SINISTRE"))
                .thenReturn(List.of(
                        ReferenceItem.builder().code("PANNE").label("Panne").build(),
                        ReferenceItem.builder().code("ACCIDENT").label("Accident").build(),
                        ReferenceItem.builder().code("USURE").label("Usure").build(),
                        ReferenceItem.builder().code("DEFAUT_FABRICATION").label("Défaut de fabrication").build(),
                        ReferenceItem.builder().code("VOL").label("Vol").build(),
                        ReferenceItem.builder().code("AUTRE").label("Autre").build()));
    }

    @Test
    @DisplayName("getReferenceData — retourne toutes les listes non nulles")
    void referenceDataNotNull() {
        ReferenceDataResponse data = service.getReferenceData();

        assertNotNull(data.getTypesPiece());
        assertNotNull(data.getCategoriesClient());
        assertNotNull(data.getTypesSinistre());
        assertNotNull(data.getChampsDisponibles());
        assertNotNull(data.getOperateursComparaison());
        assertNotNull(data.getOperateursLogiques());
        assertNotNull(data.getActions());
    }

    @Test
    @DisplayName("Types de pièce — contient les 6 valeurs CDC 4.B")
    void typePieceValues() {
        ReferenceDataResponse data = service.getReferenceData();

        assertEquals(6, data.getTypesPiece().size());
        assertTrue(data.getTypesPiece().stream().anyMatch(i -> "MECANIQUE".equals(i.getCode())));
        assertTrue(data.getTypesPiece().stream().anyMatch(i -> "ELECTRIQUE".equals(i.getCode())));
        assertTrue(data.getTypesPiece().stream().anyMatch(i -> "CARROSSERIE".equals(i.getCode())));
        assertTrue(data.getTypesPiece().stream().anyMatch(i -> "ELECTRONIQUE".equals(i.getCode())));
        assertTrue(data.getTypesPiece().stream().anyMatch(i -> "PNEUMATIQUE".equals(i.getCode())));
        assertTrue(data.getTypesPiece().stream().anyMatch(i -> "AUTRE".equals(i.getCode())));
    }

    @Test
    @DisplayName("Catégories client — contient STANDARD, PREMIUM, VIP, PROFESSIONNEL")
    void categorieClientValues() {
        ReferenceDataResponse data = service.getReferenceData();

        assertEquals(4, data.getCategoriesClient().size());
        assertTrue(data.getCategoriesClient().stream().anyMatch(i -> "STANDARD".equals(i.getCode())));
        assertTrue(data.getCategoriesClient().stream().anyMatch(i -> "VIP".equals(i.getCode())));
    }

    @Test
    @DisplayName("Types de sinistre — contient les 6 valeurs CDC 4.B")
    void typeSinistreValues() {
        ReferenceDataResponse data = service.getReferenceData();

        assertEquals(6, data.getTypesSinistre().size());
        assertTrue(data.getTypesSinistre().stream().anyMatch(i -> "PANNE".equals(i.getCode())));
        assertTrue(data.getTypesSinistre().stream().anyMatch(i -> "DEFAUT_FABRICATION".equals(i.getCode())));
        assertTrue(data.getTypesSinistre().stream().anyMatch(i -> "VOL".equals(i.getCode())));
    }

    @Test
    @DisplayName("Opérateurs de comparaison — contient les 8 opérateurs CDC 4.A")
    void operateursComparaison() {
        ReferenceDataResponse data = service.getReferenceData();

        assertEquals(8, data.getOperateursComparaison().size());
        assertTrue(data.getOperateursComparaison().stream().anyMatch(i -> "EQ".equals(i.getCode())));
        assertTrue(data.getOperateursComparaison().stream().anyMatch(i -> "IN".equals(i.getCode())));
    }

    @Test
    @DisplayName("Champs disponibles — contient les 6 champs avec types corrects")
    void champsDisponibles() {
        ReferenceDataResponse data = service.getReferenceData();

        assertEquals(6, data.getChampsDisponibles().size());

        var typePiece = data.getChampsDisponibles().stream()
                .filter(f -> "typePiece".equals(f.getField())).findFirst();
        assertTrue(typePiece.isPresent());
        assertEquals("enum", typePiece.get().getType());
        assertEquals(6, typePiece.get().getValues().size());

        var age = data.getChampsDisponibles().stream()
                .filter(f -> "ageVehiculeAns".equals(f.getField())).findFirst();
        assertTrue(age.isPresent());
        assertEquals("number", age.get().getType());
    }

    @Test
    @DisplayName("Actions — contient FAVORABLE, DEFAVORABLE, ETUDE")
    void actions() {
        ReferenceDataResponse data = service.getReferenceData();

        assertEquals(3, data.getActions().size());
        assertTrue(data.getActions().stream().anyMatch(i -> "FAVORABLE".equals(i.getCode())));
        assertTrue(data.getActions().stream().anyMatch(i -> "DEFAVORABLE".equals(i.getCode())));
        assertTrue(data.getActions().stream().anyMatch(i -> "ETUDE".equals(i.getCode())));
    }
}
