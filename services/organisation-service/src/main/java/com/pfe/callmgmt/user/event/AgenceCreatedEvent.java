package com.pfe.callmgmt.user.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgenceCreatedEvent implements Serializable {

    private String agenceId;
    private String nom;
    private String telephone;
    private String email;
    private String adresse;
}
