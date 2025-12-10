class AzsApiClient {
    constructor() {
        this.baseUrl = 'http://localhost:8080/api';
        this.token = localStorage.getItem('azs_token');
        this.userData = JSON.parse(localStorage.getItem('azs_user') || '{}');
    }

    async request(endpoint, options = {}) {
        try {
            const url = `${this.baseUrl}${endpoint}`;
            
            const headers = {
                'Content-Type': 'application/json',
                ...options.headers,
            };
            
            if (this.token) {
                headers['Authorization'] = `Bearer ${this.token}`;
            }
            
            const response = await fetch(url, {
                ...options,
                headers,
            });
            
            if (!response.ok) {
                if (response.status === 401) {
                    this.logout();
                    throw new Error('Сессия истекла. Пожалуйста, войдите снова.');
                }
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return response.json();
        } catch (error) {
            console.error('API Request error:', error);
            throw error;
        }
    }

    // Регистрация пользователя
    async register(userData) {
        try {
            const response = await this.request('/users/register', {
                method: 'POST',
                body: JSON.stringify({
                    username: userData.phone,
                    phone: userData.phone,
                    name: userData.name,
                    password: userData.password
                })
            });
            
            if (response.success) {
                this.token = response.token;
                this.userData = response.user || { id: response.userId };
                
                localStorage.setItem('azs_token', this.token);
                localStorage.setItem('azs_user', JSON.stringify(this.userData));
                
                return response;
            } else {
                throw new Error(response.message || 'Ошибка регистрации');
            }
        } catch (error) {
            console.error('Register error:', error);
            throw error;
        }
    }

    // Вход пользователя
    async login(phone, password) {
        try {
            const response = await this.request('/users/login', {
                method: 'POST',
                body: JSON.stringify({
                    phone: phone,
                    password: password
                })
            });
            
            if (response.success) {
                this.token = response.token;
                this.userData = response.user;
                
                localStorage.setItem('azs_token', this.token);
                localStorage.setItem('azs_user', JSON.stringify(this.userData));
                
                return response;
            } else {
                throw new Error(response.message || 'Ошибка входа');
            }
        } catch (error) {
            console.error('Login error:', error);
            throw error;
        }
    }

    // Получение профиля пользователя
    async getProfile() {
        try {
            const response = await this.request('/users/profile');
            if (response.success) {
                this.userData = response.user;
                localStorage.setItem('azs_user', JSON.stringify(this.userData));
                return response;
            } else {
                throw new Error(response.message || 'Ошибка получения профиля');
            }
        } catch (error) {
            console.error('Get profile error:', error);
            throw error;
        }
    }

    // Обновление профиля
    async updateProfile(updateData) {
        try {
            const response = await this.request('/users/update', {
                method: 'PUT',
                body: JSON.stringify(updateData)
            });
            
            if (response.success) {
                // Обновляем локальные данные
                Object.assign(this.userData, updateData);
                localStorage.setItem('azs_user', JSON.stringify(this.userData));
                return response;
            } else {
                throw new Error(response.message || 'Ошибка обновления профиля');
            }
        } catch (error) {
            console.error('Update profile error:', error);
            throw error;
        }
    }

    // Получение истории транзакций
    async getTransactions(limit = 50) {
        try {
            const response = await this.request(`/users/transactions?limit=${limit}`);
            return response;
        } catch (error) {
            console.error('Get transactions error:', error);
            throw error;
        }
    }

    // Получение цен на топливо
    async getFuelPrices() {
        try {
            const response = await this.request('/fuel');
            return response;
        } catch (error) {
            console.error('Get fuel prices error:', error);
            throw error;
        }
    }

    // Получение списка АЗС
    async getStations() {
        try {
            const response = await this.request('/azs');
            return response;
        } catch (error) {
            console.error('Get stations error:', error);
            throw error;
        }
    }

    // Генерация QR-кода
    async generateQrCode(azsId, nozzleNumber) {
        try {
            const response = await this.request('/qr/generate', {
                method: 'POST',
                body: JSON.stringify({
                    azs_id: azsId,
                    nozzle_number: nozzleNumber,
                    user_id: this.userData.id || 0
                })
            });
            return response;
        } catch (error) {
            console.error('Generate QR error:', error);
            throw error;
        }
    }

    // Создание транзакции
    async createTransaction(transactionData) {
        try {
            const response = await this.request('/transactions', {
                method: 'POST',
                body: JSON.stringify(transactionData)
            });
            return response;
        } catch (error) {
            console.error('Create transaction error:', error);
            throw error;
        }
    }

    // Генерация чека
    async generateReceipt(transactionId) {
        try {
            const response = await this.request('/receipts/generate', {
                method: 'POST',
                body: JSON.stringify({ transaction_id: transactionId })
            });
            return response;
        } catch (error) {
            console.error('Generate receipt error:', error);
            throw error;
        }
    }

    // Проверка соединения с сервером
    async checkConnection() {
        try {
            const response = await fetch(`${this.baseUrl}/health`, {
                method: 'GET',
                timeout: 5000
            });
            return response.ok;
        } catch (error) {
            return false;
        }
    }

    // Выход из системы
    logout() {
        this.token = null;
        this.userData = {};
        localStorage.removeItem('azs_token');
        localStorage.removeItem('azs_user');
    }

    // Проверка авторизации
    isAuthenticated() {
        return !!this.token && this.userData.id;
    }

    // Получение текущего пользователя
    getCurrentUser() {
        return this.userData;
    }

    // Обновление пользовательских данных
    setUserData(userData) {
        this.userData = userData;
        localStorage.setItem('azs_user', JSON.stringify(userData));
    }
}

// Создаем глобальный экземпляр API клиента
window.azsApi = new AzsApiClient();