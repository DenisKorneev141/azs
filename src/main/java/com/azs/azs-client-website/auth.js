class AuthManager {
    constructor() {
        this.api = window.azsApi;
        this.init();
    }

    init() {
        this.setupEventListeners();
        this.checkAuthState();
    }

    setupEventListeners() {
        // Кнопки входа и регистрации
        document.getElementById('loginBtn')?.addEventListener('click', () => this.showLoginModal());
        document.getElementById('registerBtn')?.addEventListener('click', () => this.showRegisterModal());
        document.getElementById('logoutBtn')?.addEventListener('click', () => this.logout());

        // Переключение между модальными окнами
        document.getElementById('showRegister')?.addEventListener('click', (e) => {
            e.preventDefault();
            this.closeAllModals();
            this.showRegisterModal();
        });

        document.getElementById('showLogin')?.addEventListener('click', (e) => {
            e.preventDefault();
            this.closeAllModals();
            this.showLoginModal();
        });

        // Формы
        document.getElementById('loginForm')?.addEventListener('submit', (e) => this.handleLogin(e));
        document.getElementById('registerForm')?.addEventListener('submit', (e) => this.handleRegister(e));

        // Закрытие модальных окон
        document.querySelectorAll('.modal .close').forEach(closeBtn => {
            closeBtn.addEventListener('click', () => this.closeAllModals());
        });

        // Закрытие по клику вне модального окна
        window.addEventListener('click', (e) => {
            document.querySelectorAll('.modal').forEach(modal => {
                if (e.target === modal) {
                    this.closeAllModals();
                }
            });
        });
    }

    checkAuthState() {
        if (this.api.isAuthenticated()) {
            this.showUserInfo();
        } else {
            this.showAuthButtons();
        }
    }

    showUserInfo() {
        const user = this.api.getCurrentUser();
        const authButtons = document.getElementById('authButtons');
        const userInfo = document.getElementById('userInfo');
        
        if (authButtons && userInfo) {
            authButtons.style.display = 'none';
            userInfo.style.display = 'flex';
            
            document.getElementById('userName').textContent = user.name || 'Пользователь';
            document.getElementById('userBalance').textContent = 
                `${(user.balance || 0).toFixed(2)} бонусов`;
        }
    }

    showAuthButtons() {
        const authButtons = document.getElementById('authButtons');
        const userInfo = document.getElementById('userInfo');
        
        if (authButtons && userInfo) {
            authButtons.style.display = 'flex';
            userInfo.style.display = 'none';
        }
    }

    showLoginModal() {
        this.closeAllModals();
        document.getElementById('loginModal').style.display = 'flex';
    }

    showRegisterModal() {
        this.closeAllModals();
        document.getElementById('registerModal').style.display = 'flex';
    }

    closeAllModals() {
        document.querySelectorAll('.modal').forEach(modal => {
            modal.style.display = 'none';
        });
        
        // Очистка форм
        document.getElementById('loginForm')?.reset();
        document.getElementById('registerForm')?.reset();
    }

    async handleLogin(e) {
        e.preventDefault();
        
        const phone = document.getElementById('loginPhone').value;
        const password = document.getElementById('loginPassword').value;
        
        if (!phone || !password) {
            this.showNotification('Заполните все поля', 'error');
            return;
        }

        try {
            const response = await this.api.login(phone, password);
            
            this.showNotification('Вход выполнен успешно!', 'success');
            this.closeAllModals();
            this.showUserInfo();
            
            // Обновляем данные на странице
            window.location.reload();
            
        } catch (error) {
            this.showNotification(error.message || 'Ошибка входа', 'error');
        }
    }

    async handleRegister(e) {
        e.preventDefault();
        
        const name = document.getElementById('regName').value;
        const phone = document.getElementById('regPhone').value;
        const password = document.getElementById('regPassword').value;
        const confirmPassword = document.getElementById('regPasswordConfirm').value;
        
        if (!name || !phone || !password || !confirmPassword) {
            this.showNotification('Заполните все поля', 'error');
            return;
        }
        
        if (password !== confirmPassword) {
            this.showNotification('Пароли не совпадают', 'error');
            return;
        }
        
        if (password.length < 6) {
            this.showNotification('Пароль должен содержать минимум 6 символов', 'error');
            return;
        }

        try {
            // Проверяем, не зарегистрирован ли уже пользователь
            const existingUser = await this.api.searchUserByPhone(phone);
            
            if (existingUser.success) {
                this.showNotification('Пользователь с таким номером уже существует', 'error');
                return;
            }
            
            // Регистрируем нового пользователя
            const userData = {
                username: phone,
                phone: phone,
                name: name,
                password: password
            };
            
            const response = await this.api.register(userData);
            
            if (response.success) {
                this.showNotification('Регистрация успешна! Теперь вы можете войти.', 'success');
                this.closeAllModals();
                this.showLoginModal();
            } else {
                throw new Error(response.message || 'Ошибка регистрации');
            }
            
        } catch (error) {
            this.showNotification(error.message || 'Ошибка регистрации', 'error');
        }
    }

    logout() {
        this.api.logout();
        this.showAuthButtons();
        this.showNotification('Вы вышли из системы', 'success');
        
        // Обновляем страницу
        window.location.reload();
    }

    showNotification(message, type = 'info') {
        // Удаляем предыдущие уведомления
        const existingNotifications = document.querySelectorAll('.notification');
        existingNotifications.forEach(notification => notification.remove());
        
        // Создаем новое уведомление
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        notification.textContent = message;
        
        document.body.appendChild(notification);
        
        // Автоматическое удаление через 5 секунд
        setTimeout(() => {
            notification.remove();
        }, 5000);
    }
}

// Инициализация менеджера авторизации
window.addEventListener('DOMContentLoaded', () => {
    window.authManager = new AuthManager();
});