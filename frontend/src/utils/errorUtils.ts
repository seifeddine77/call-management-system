import axios from 'axios';

/**
 * Extrait un message d'erreur lisible et sécurisé à partir d'une erreur d'API.
 *
 * @param err L'erreur attrapée dans le bloc catch.
 * @param defaultMessage Le message par défaut à afficher si l'erreur n'est pas détaillée.
 * @returns Un message d'erreur clair en français.
 */
export function extractErrorMessage(err: unknown, defaultMessage: string = 'Une erreur est survenue'): string {
  if (axios.isAxiosError(err)) {
    // Si le serveur a renvoyé un message d'erreur spécifique dans le body
    const serverError = err.response?.data?.error;
    if (serverError && typeof serverError === 'string') {
      return serverError;
    }

    const serverMessage = err.response?.data?.message;
    if (serverMessage && typeof serverMessage === 'string') {
      return serverMessage;
    }

    // Gestion des codes HTTP standard de manière conviviale
    switch (err.response?.status) {
      case 400:
        // Si erreurs de validation par champ
        if (err.response.data?.fieldErrors) {
          const errors = err.response.data.fieldErrors;
          const firstErr = Object.values(errors)[0];
          if (typeof firstErr === 'string') return firstErr;
        }
        return 'Requête invalide.';
      case 401:
        return 'Email ou mot de passe incorrect.';
      case 403:
        return "Accès refusé. Vous n'avez pas les autorisations nécessaires.";
      case 404:
        return 'Ressource introuvable.';
      case 409:
        return 'Cette ressource existe déjà ou est en conflit.';
      case 500:
        return 'Erreur interne du serveur. Veuillez réessayer plus tard.';
      default:
        break;
    }

    // Si pas de réponse du serveur (problème réseau ou serveur éteint)
    if (!err.response) {
      return 'Impossible de contacter le serveur. Veuillez vérifier votre connexion.';
    }
  }

  if (err instanceof Error) {
    return err.message;
  }

  return defaultMessage;
}
