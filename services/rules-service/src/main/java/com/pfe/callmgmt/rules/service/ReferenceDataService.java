package com.pfe.callmgmt.rules.service;

import com.pfe.callmgmt.rules.dto.ReferenceDataResponse;
import com.pfe.callmgmt.rules.dto.ReferenceDataResponse.FieldDefinition;
import com.pfe.callmgmt.rules.dto.ReferenceDataResponse.ReferenceItem;
import com.pfe.callmgmt.rules.repository.ReferenceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CDC 4.B — Service de paramétrage des listes de référence.
 * Lit les données depuis PostgreSQL (table reference_items).
 * Fournit les valeurs configurables pour l'éditeur de règles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataService {

    private final ReferenceItemRepository referenceItemRepository;

    /**
     * Retourne toutes les listes de référence pour l'éditeur de règles front-end.
     * Les données proviennent de la BDD (administrables via l'interface Paramétrage).
     */
    public ReferenceDataResponse getReferenceData() {
        return ReferenceDataResponse.builder()
                .typesPiece(getItemsByCategory("TYPE_PIECE"))
                .categoriesClient(getItemsByCategory("CATEGORIE_CLIENT"))
                .typesSinistre(getItemsByCategory("TYPE_SINISTRE"))
                .champsDisponibles(getChampsDisponibles())
                .operateursComparaison(getOperateursComparaison())
                .operateursLogiques(getOperateursLogiques())
                .actions(getActions())
                .build();
    }

    /**
     * Récupère les éléments actifs d'une catégorie depuis la BDD.
     */
    private List<ReferenceItem> getItemsByCategory(String category) {
        return referenceItemRepository.findByCategoryAndActifTrueOrderByDisplayOrderAsc(category)
                .stream()
                .map(entity -> ReferenceItem.builder()
                        .code(entity.getCode())
                        .label(entity.getLabel())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Champs disponibles pour les conditions des règles.
     * Dynamiquement récupérés depuis la BDD (catégorie FIELD_DEFINITION).
     * Le champ "description" encode le type et la catégorie enum :
     *   - "number"             → champ numérique
     *   - "string"             → champ texte libre
     *   - "enum:TYPE_PIECE"    → champ enum, valeurs lues depuis la catégorie TYPE_PIECE
     */
    private List<FieldDefinition> getChampsDisponibles() {
        List<com.pfe.callmgmt.rules.entity.ReferenceItem> fieldDefs =
                referenceItemRepository.findByCategoryAndActifTrueOrderByDisplayOrderAsc("FIELD_DEFINITION");

        return fieldDefs.stream().map(fd -> {
            String typeDesc = fd.getDescription() != null ? fd.getDescription().trim() : "string";
            String type;
            List<String> values = null;

            if (typeDesc.startsWith("enum:")) {
                type = "enum";
                String enumCategory = typeDesc.substring(5); // ex: "TYPE_PIECE"
                values = getActiveCodesForCategory(enumCategory);
            } else {
                type = typeDesc; // "number" ou "string"
            }

            FieldDefinition.FieldDefinitionBuilder builder = FieldDefinition.builder()
                    .field(fd.getCode())
                    .label(fd.getLabel())
                    .type(type);

            if (values != null) {
                builder.values(values);
            }

            return builder.build();
        }).collect(Collectors.toList());
    }

    private List<String> getActiveCodesForCategory(String category) {
        return referenceItemRepository.findByCategoryAndActifTrueOrderByDisplayOrderAsc(category)
                .stream()
                .map(com.pfe.callmgmt.rules.entity.ReferenceItem::getCode)
                .collect(Collectors.toList());
    }

    // Opérateurs de comparaison (statiques — pas besoin d'être en BDD)
    private List<ReferenceItem> getOperateursComparaison() {
        return List.of(
                ReferenceItem.builder().code("EQ").label("Égal à (=)").build(),
                ReferenceItem.builder().code("NEQ").label("Différent de (≠)").build(),
                ReferenceItem.builder().code("LT").label("Inférieur à (<)").build(),
                ReferenceItem.builder().code("GT").label("Supérieur à (>)").build(),
                ReferenceItem.builder().code("LTE").label("Inférieur ou égal (≤)").build(),
                ReferenceItem.builder().code("GTE").label("Supérieur ou égal (≥)").build(),
                ReferenceItem.builder().code("CONTAINS").label("Contient").build(),
                ReferenceItem.builder().code("IN").label("Dans la liste").build());
    }

    // Opérateurs logiques (statiques)
    private List<ReferenceItem> getOperateursLogiques() {
        return List.of(
                ReferenceItem.builder().code("AND").label("ET (toutes les conditions)").build(),
                ReferenceItem.builder().code("OR").label("OU (au moins une condition)").build());
    }

    // Actions résultantes (statiques)
    private List<ReferenceItem> getActions() {
        return List.of(
                ReferenceItem.builder().code("FAVORABLE").label("Favorable — Demande acceptée").build(),
                ReferenceItem.builder().code("DEFAVORABLE").label("Défavorable — Demande refusée").build(),
                ReferenceItem.builder().code("ETUDE").label("Étude — Analyse complémentaire requise").build());
    }
}
