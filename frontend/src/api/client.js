import axios from 'axios';

/**
 * Pre-configured Axios instance for all API calls.
 *
 * baseURL points to the Spring Boot backend running on port 8080.
 *
 * The request interceptor automatically attaches the JWT from localStorage
 * as an "Authorization: Bearer <token>" header on every outgoing request.
 * This means no page or component needs to manually add the header.
 *
 * The response interceptor catches 401 (token expired or invalid) and
 * redirects the user back to the login page automatically.
 */
const api = axios.create({
  baseURL: 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Attach JWT on every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('gl_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle token expiry globally
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('gl_token');
      localStorage.removeItem('gl_user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
