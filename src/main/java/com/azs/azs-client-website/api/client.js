class AzsApiClient {
    constructor() {
        this.baseUrl = 'http://localhost:8080/api';
        this.token = localStorage.getItem('azs_token');
        this.userData = JSON.parse(localStorage.getItem('azs_user') || '{}');
    }

    async request(endpoint, options = {}) {
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
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        return response.json();
    }

    // Аутентификация
    async login(phone, password) {
        try {
            const response = await this.request('/auth', {
                method: 'POST',
                body: JSON.stringify({
                    username: phone,
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
                throw new Error(response.message || 'Ошибка авторизации');
            }
        } catch (error) {
            console.error('Login error:', error);
            throw error;
        }
    }

    async register(userData) {
        try {
            const response = await this.request('/users', {
                method: 'POST',
                body: JSON.stringify(userData)
            });
            
            return response;
        } catch (error) {
            console.error('Registration error:', error);
            throw error;
        }
    }

    // Поиск пользователя по телефону
    async searchUserByPhone(phone) {
        try {
            const response = await this.request(`/users/search?phone=${encodeURIComponent(phone)}`);
            return response;
        } catch (error) {
            console.error('Search user error:', error);
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
    async getAZSList() {
        try {
            const response = await this.request('/azs');
            return response;
        } catch (error) {
            console.error('Get AZS list error:', error);
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

    // Генерация QR-кода
    async getQrCode(azsId, nozzleNumber) {
        try {
            const response = await this.request(`/qr/${azsId}/${nozzleNumber}`);
            return response;
        } catch (error) {
            console.error('Get QR code error:', error);
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

    // Получение истории транзакций
    async getTransactionHistory(userId) {
        try {
            const response = await this.request(`/users/${userId}/transactions`);
            return response;
        } catch (error) {
            console.error('Get transaction history error:', error);
            throw error;
        }
    }

    // Обновление баланса пользователя
    async updateUserBalance(userId, updateData) {
        try {
            const response = await this.request(`/users/${userId}/update-balance`, {
                method: 'POST',
                body: JSON.stringify(updateData)
            });
            return response;
        } catch (error) {
            console.error('Update user balance error:', error);
            throw error;
        }
    }

    // Получение данных пользователя
    async getUserProfile(userId) {
        try {
            const response = await this.request(`/users/${userId}`);
            return response;
        } catch (error) {
            console.error('Get user profile error:', error);
            throw error;
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
        return !!this.token;
    }

    // Получение текущего пользователя
    getCurrentUser() {
        return this.userData;
    }
}

// Создаем глобальный экземпляр API клиента
window.azsApi = new AzsApiClient();