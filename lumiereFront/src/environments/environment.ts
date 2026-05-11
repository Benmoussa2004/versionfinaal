// ⚠️ Modifier uniquement cette ligne pour changer l'adresse IP du backend
const BASE_URL = 'http://172.18.101.192:8090';

export const environment = {
    production: false,

    baseUrl: BASE_URL,

    apiUrl: `${BASE_URL}/api/v1`,
    v1ApiUrl: `${BASE_URL}/api/v1`,
    authUrl: `${BASE_URL}/api/v1/auth`,
    adminUrl: `${BASE_URL}/api/v1/admin`,
    demoUrl: `${BASE_URL}/api/v1/demo`
};
