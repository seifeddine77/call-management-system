# Eureka Server (Service Discovery)

**Port :** `8761` · **Stack :** Spring Cloud Netflix Eureka · **Base de données :** aucune

## Rôle
Annuaire de services. Chaque microservice s'y enregistre au démarrage ; le gateway et les clients Feign résolvent ainsi les instances par nom logique (`lb://organisation-service`) sans adresses IP en dur.

## Points notables (soutenance)
- Découplage et **résilience** : on peut redémarrer/scaler un service sans reconfigurer les autres.
- Tableau de bord : http://localhost:8761 (liste des instances enregistrées).
- Après un redémarrage, la propagation des baux Eureka prend quelques dizaines de secondes (d'où les `fallback` Feign transitoires possibles).

## Dépendances
Aucune (les autres services dépendent de lui).
