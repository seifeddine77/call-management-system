import axios from 'axios';

/**
 * Extrait un message d'erreur lisible et sécurisé à partir d'une erreur d'API.
 *
 * @param err L'erreur attrapée dans le bloc catch.
 * @param defaultMessage Le message par défaut à afficher si l'erreur n'est pas détaillée.
 * @returns Un message d'erreur clair en français.
 */
function extractFromResponseData(data: any): string | null {
  if (!data) return null;
  
  const error = data.error;
  if (error && typeof error === 'string') {
    return error;
  }

  const message = data.message;
  if (message && typeof message === 'string') {
    return message;
  }

  return null;
}

function extractFromHttpStatus(status: number, data: any): string | null {
  switch (status) {
    case 400:
      if (data?.fieldErrors) {
        const firstErr = Object.values(data.fieldErrors)[0];
        if (typeof firstErr === 'string') {
          return firstErr;
        }
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
      return null;
  }
}

function handleAxiosError(err: any): string | null {
  if (err.response) {
    const fromData = extractFromResponseData(err.response.data);
    if (fromData) return fromData;

    const fromStatus = extractFromHttpStatus(err.response.status, err.response.data);
    if (fromStatus) return fromStatus;
  } else {
    return 'Impossible de contacter le serveur. Veuillez vérifier votre connexion.';
  }
  return null;
}

export function extractErrorMessage(err: unknown, defaultMessage: string = 'Une erreur est survenue'): string {
  if (axios.isAxiosError(err)) {
    const msg = handleAxiosError(err);
    if (msg) return msg;
  }

  if (err instanceof Error) {
    return err.message;
  }

  return defaultMessage;
}

