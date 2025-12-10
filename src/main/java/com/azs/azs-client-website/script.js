class AzsWebsite {
    constructor() {
        this.api = window.azsApi;
        this.i18n = window.i18n;
        this.currentPage = 'home';
        this.currentProfileTab = 'profile';
        this.cameraActive = false;
        this.init();
    }

    init() {
        this.setupEventListeners();
        this.setupPhoneInputs();
        this.setupTheme();
        this.setupPageNavigation();
        this.checkServerConnection();
        this.loadInitialData();
        this.checkAuthState();
    }

    setupEventListeners() {
        // Навигация
        document.querySelectorAll('.nav a').forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const page = e.currentTarget.getAttribute('data-page');
                this.switchPage(page);
            });
        });

        // Кнопки входа и регистрации
        document.getElementById('loginBtn')?.addEventListener('click', () => this.showLoginModal());
        document.getElementById('registerBtn')?.addEventListener('click', () => this.showRegisterModal());
        document.getElementById('logoutBtn')?.addEventListener('click', () => this.logout());
        document.getElementById('startRefuelBtn')?.addEventListener('click', () => this.showPaymentModal());

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

        // QR кнопки
        document.getElementById('generateQrBtn')?.addEventListener('click', () => this.generateQrCode());
        document.getElementById('scanQrBtn')?.addEventListener('click', () => this.showQrScanner());
        document.getElementById('processManualQrBtn')?.addEventListener('click', () => this.processManualQrCode());

        // Переключение вкладок профиля
        document.querySelectorAll('.profile-menu a').forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const tab = e.currentTarget.getAttribute('data-tab');
                this.switchProfileTab(tab);
            });
        });

        // Кнопки камеры
        document.getElementById('toggleCameraBtn')?.addEventListener('click', () => this.toggleCamera());
        document.getElementById('switchCameraBtn')?.addEventListener('click', () => this.switchCamera());

        // Переключение видимости пароля
        this.setupPasswordToggle();

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

        // Переключение темы
        document.getElementById('themeToggle')?.addEventListener('click', () => this.toggleTheme());
    }

    setupPhoneInputs() {
        // Инициализация телефонных инпутов с кодом Беларуси +375
        const phoneInputs = document.querySelectorAll('.phone-input');
        
        phoneInputs.forEach(input => {
            window.intlTelInput(input, {
                initialCountry: "by",
                preferredCountries: ["by", "ru", "ua"],
                separateDialCode: true,
                utilsScript: "https://cdn.jsdelivr.net/npm/intl-tel-input@19.2.16/build/js/utils.js"
            });
        });
    }

    setupPasswordToggle() {
        // Переключение видимости пароля
        const toggleLoginPassword = document.getElementById('toggleLoginPassword');
        const toggleRegPassword = document.getElementById('toggleRegPassword');
        const toggleRegPasswordConfirm = document.getElementById('toggleRegPasswordConfirm');
        
        if (toggleLoginPassword) {
            toggleLoginPassword.addEventListener('click', () => {
                const passwordInput = document.getElementById('loginPassword');
                passwordInput.type = passwordInput.type === 'password' ? 'text' : 'password';
                toggleLoginPassword.classList.toggle('fa-eye');
                toggleLoginPassword.classList.toggle('fa-eye-slash');
            });
        }
        
        if (toggleRegPassword) {
            toggleRegPassword.addEventListener('click', () => {
                const passwordInput = document.getElementById('regPassword');
                passwordInput.type = passwordInput.type === 'password' ? 'text' : 'password';
                toggleRegPassword.classList.toggle('fa-eye');
                toggleRegPassword.classList.toggle('fa-eye-slash');
            });
        }
        
        if (toggleRegPasswordConfirm) {
            toggleRegPasswordConfirm.addEventListener('click', () => {
                const passwordInput = document.getElementById('regPasswordConfirm');
                passwordInput.type = passwordInput.type === 'password' ? 'text' : 'password';
                toggleRegPasswordConfirm.classList.toggle('fa-eye');
                toggleRegPasswordConfirm.classList.toggle('fa-eye-slash');
            });
        }
    }

    setupTheme() {
        // Загрузка темы из localStorage или установка светлой по умолчанию
        const savedTheme = localStorage.getItem('theme') || 'light';
        this.setTheme(savedTheme);
        
        // Обновляем иконку переключателя темы
        this.updateThemeIcon();
    }

    setTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('theme', theme);
        this.updateThemeIcon();
    }

    toggleTheme() {
        const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';
        const newTheme = currentTheme === 'light' ? 'dark' : 'light';
        this.setTheme(newTheme);
    }

    updateThemeIcon() {
        const themeToggle = document.getElementById('themeToggle');
        if (!themeToggle) return;
        
        const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';
        const icon = themeToggle.querySelector('i');
        
        if (currentTheme === 'dark') {
            icon.classList.remove('fa-moon');
            icon.classList.add('fa-sun');
            themeToggle.title = i18n.translate('theme.light');
        } else {
            icon.classList.remove('fa-sun');
            icon.classList.add('fa-moon');
            themeToggle.title = i18n.translate('theme.dark');
        }
    }

    async checkServerConnection() {
        try {
            const isConnected = await this.api.checkConnection();
            if (!isConnected) {
                this.showNotification(i18n.translate('notification.noConnection'), 'error');
            }
        } catch (error) {
            console.error('Server connection check failed:', error);
        }
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
                `${(user.balance || 0).toFixed(2)} ${i18n.translate('profile.bonuses')}`;
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

    setupPageNavigation() {
        // Показываем только активную страницу
        const sections = document.querySelectorAll('#fuelSection, #paymentSection, #stationsSection, #profileSection');
        
        sections.forEach(section => {
            section.style.display = 'none';
        });
        
        // Показываем главную страницу по умолчанию
        this.showPage('home');
    }

    switchPage(page) {
        this.currentPage = page;
        
        // Обновляем активную ссылку в навигации
        document.querySelectorAll('.nav a').forEach(link => {
            link.classList.remove('active');
            if (link.getAttribute('data-page') === page) {
                link.classList.add('active');
            }
        });
        
        this.showPage(page);
    }

    showPage(page) {
        // Скрываем все секции
        const sections = document.querySelectorAll('#fuelSection, #paymentSection, #stationsSection, #profileSection');
        sections.forEach(section => {
            section.style.display = 'none';
        });
        
        // Показываем нужную секцию
        switch(page) {
            case 'fuel':
                document.getElementById('fuelSection').style.display = 'block';
                this.loadFuelPrices();
                break;
            case 'payment':
                document.getElementById('paymentSection').style.display = 'block';
                break;
            case 'stations':
                document.getElementById('stationsSection').style.display = 'block';
                this.loadStations();
                break;
            case 'profile':
                if (this.api.isAuthenticated()) {
                    document.getElementById('profileSection').style.display = 'block';
                    this.loadProfileData();
                } else {
                    this.showLoginModal();
                    this.switchPage('home');
                }
                break;
            case 'home':
            default:
                // Главная страница показывает все секции
                sections.forEach(section => {
                    section.style.display = 'block';
                });
                this.loadFuelPrices();
                this.loadStations();
                break;
        }
    }

    async loadInitialData() {
        // Загружаем данные при запуске
        await Promise.all([
            this.loadFuelPrices(),
            this.loadStations()
        ]);
    }

    async loadFuelPrices() {
        try {
            const container = document.getElementById('fuelPrices');
            if (!container) return;
            
            container.innerHTML = `
                <div class="loading">
                    <i class="fas fa-spinner fa-spin"></i>
                    <span>${i18n.translate('common.loading')}</span>
                </div>
            `;
            
            const response = await this.api.getFuelPrices();
            
            if (response.success && response.data) {
                this.displayFuelPrices(response.data);
            } else {
                this.displayFuelPrices(this.getSampleFuelPrices());
            }
        } catch (error) {
            console.error('Failed to load fuel prices:', error);
            this.displayFuelPrices(this.getSampleFuelPrices());
        }
    }

    displayFuelPrices(fuelData) {
        const container = document.getElementById('fuelPrices');
        if (!container) return;
        
        container.innerHTML = '';
        
        const fuelTypes = [
            { key: 'ai92', name: i18n.translate('fuel.ai92'), icon: 'fa-gas-pump' },
            { key: 'ai95', name: i18n.translate('fuel.ai95'), icon: 'fa-gas-pump' },
            { key: 'ai98', name: i18n.translate('fuel.ai98'), icon: 'fa-gas-pump' },
            { key: 'ai100', name: i18n.translate('fuel.ai100'), icon: 'fa-gas-pump' },
            { key: 'dt', name: i18n.translate('fuel.diesel'), icon: 'fa-oil-can' },
           // { key: 'dtk5', name: i18n.translate('fuel.dtk5'), icon: 'fa-oil-can' }
        ];
        
        fuelTypes.forEach(fuel => {
            let price = '0.00';
            let status = 'available';
            
            if (fuelData[fuel.key]) {
                price = fuelData[fuel.key];
            } else if (fuelData[fuel.key + '_raw']) {
                price = fuelData[fuel.key + '_raw'].toFixed(2);
            }
            
            const card = document.createElement('div');
            card.className = 'fuel-card';
            
            card.innerHTML = `
                <div class="fuel-status ${status}">${i18n.translate(`fuel.${status}`)}</div>
                <i class="fas ${fuel.icon} fa-2x" style="color: var(--primary-color); margin-bottom: 15px;"></i>
                <div class="fuel-type">${fuel.name}</div>
                <div class="fuel-price">${price} <span>${i18n.translate('fuel.perLiter')}</span></div>
            `;
            
            container.appendChild(card);
        });
    }

    getSampleFuelPrices() {
        return {
            ai92: '2.50',
            ai95: '2.80',
            ai98: '3.10',
            ai100: '3.50',
            dt: '2.90',
            dtk5: '3.00',
            ai92_raw: 2.50,
            ai95_raw: 2.80,
            ai98_raw: 3.10,
            ai100_raw: 3.50,
            dt_raw: 2.90,
            dtk5_raw: 3.00
        };
    }

    async loadStations() {
        try {
            const container = document.getElementById('stationsList');
            if (!container) return;
            
            container.innerHTML = `
                <div class="loading">
                    <i class="fas fa-spinner fa-spin"></i>
                    <span>${i18n.translate('common.loading')}</span>
                </div>
            `;
            
            const response = await this.api.getStations();
            
            if (response.success && response.data) {
                this.displayStations(response.data);
            } else {
                this.displayStations(this.getSampleStations());
            }
        } catch (error) {
            console.error('Failed to load stations:', error);
            this.displayStations(this.getSampleStations());
        }
    }

    displayStations(stations) {
        const container = document.getElementById('stationsList');
        if (!container) return;
        
        container.innerHTML = '';
        
        stations.forEach(station => {
            const stationItem = document.createElement('div');
            stationItem.className = 'station-item';
            stationItem.innerHTML = `
                <h4>${station.name}</h4>
                <p>${station.address}</p>
                <p><small>${i18n.translate('stations.nozzles')}: ${station.nozzle_count || 4}</small></p>
                <button class="btn btn-primary btn-small" style="margin-top: 10px;"
                        onclick="azsWebsite.startRefueling(${station.id})">
                    <i class="fas fa-play"></i> ${i18n.translate('hero.startRefuel')}
                </button>
            `;
            
            container.appendChild(stationItem);
        });
    }

    getSampleStations() {
        return [
            { id: 1, name: 'АЗС №1 Центральная', address: 'г. Минск, ул. Центральная, 1', nozzle_count: 4 },
            { id: 2, name: 'АЗС №2 Западная', address: 'г. Минск, ул. Западная, 25', nozzle_count: 3 },
            { id: 3, name: 'АЗС №3 Восточная', address: 'г. Минск, ул. Восточная, 42', nozzle_count: 4 },
            { id: 4, name: 'АЗС №4 Северная', address: 'г. Минск, ул. Северная, 15', nozzle_count: 2 }
        ];
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
        
        // Выключаем камеру если она активна
        if (this.cameraActive) {
            this.stopCamera();
        }
    }

    async handleLogin(e) {
        e.preventDefault();
        
        const phoneInput = document.getElementById('loginPhone');
        const passwordInput = document.getElementById('loginPassword');
        const submitBtn = document.getElementById('loginSubmitBtn');
        
        const phone = phoneInput.value;
        const password = passwordInput.value;
        
        if (!phone || !password) {
            this.showNotification('Заполните все поля', 'error');
            return;
        }

        try {
            submitBtn.disabled = true;
            submitBtn.innerHTML = `<i class="fas fa-spinner fa-spin"></i> ${i18n.translate('common.loading')}`;
            
            const response = await this.api.login(phone, password);
            
            this.showNotification(i18n.translate('notification.loginSuccess'), 'success');
            this.closeAllModals();
            this.showUserInfo();
            
            // Обновляем данные на странице
            this.loadProfileData();
            
        } catch (error) {
            this.showNotification(error.message || i18n.translate('notification.loginError'), 'error');
        } finally {
            submitBtn.disabled = false;
            submitBtn.innerHTML = `<i class="fas fa-sign-in-alt"></i> ${i18n.translate('auth.login')}`;
        }
    }

    async handleRegister(e) {
        e.preventDefault();
        
        const nameInput = document.getElementById('regName');
        const phoneInput = document.getElementById('regPhone');
        const passwordInput = document.getElementById('regPassword');
        const confirmPasswordInput = document.getElementById('regPasswordConfirm');
        const submitBtn = document.getElementById('registerSubmitBtn');
        
        const name = nameInput.value.trim();
        const phone = phoneInput.value;
        const password = passwordInput.value;
        const confirmPassword = confirmPasswordInput.value;
        
        if (!name || !phone || !password || !confirmPassword) {
            this.showNotification('Заполните все поля', 'error');
            return;
        }
        
        if (password !== confirmPassword) {
            this.showNotification(i18n.translate('notification.passwordMismatch'), 'error');
            return;
        }
        
        if (password.length < 6) {
            this.showNotification(i18n.translate('auth.passwordHint'), 'error');
            return;
        }

        try {
            submitBtn.disabled = true;
            submitBtn.innerHTML = `<i class="fas fa-spinner fa-spin"></i> ${i18n.translate('common.loading')}`;
            
            const userData = {
                name: name,
                phone: phone,
                password: password
            };
            
            const response = await this.api.register(userData);
            
            this.showNotification(i18n.translate('notification.registerSuccess'), 'success');
            this.closeAllModals();
            this.showUserInfo();
            
            // Обновляем данные на странице
            this.loadProfileData();
            
        } catch (error) {
            this.showNotification(error.message || i18n.translate('notification.registerError'), 'error');
        } finally {
            submitBtn.disabled = false;
            submitBtn.innerHTML = `<i class="fas fa-user-plus"></i> ${i18n.translate('auth.register')}`;
        }
    }

    logout() {
        this.api.logout();
        this.showAuthButtons();
        this.showNotification(i18n.translate('notification.logoutSuccess'), 'success');
        
        // Обновляем страницу
        this.switchPage('home');
        this.loadProfileData();
    }

    async loadProfileData() {
        if (!this.api.isAuthenticated()) {
            this.displayUnauthenticatedProfile();
            return;
        }
        
        try {
            const response = await this.api.getProfile();
            
            if (response.success) {
                this.displayProfileData(response.user);
            }
        } catch (error) {
            console.error('Failed to load profile:', error);
            this.displayProfileData(this.api.getCurrentUser());
        }
        
        // Загружаем историю транзакций если активна вкладка
        if (this.currentProfileTab === 'transactions') {
            await this.loadTransactionHistory();
        }
    }

    displayUnauthenticatedProfile() {
        const tabContent = document.getElementById('profileTabContent');
        if (!tabContent) return;
        
        tabContent.innerHTML = `
            <div class="welcome-message">
                <i class="fas fa-user-lock fa-3x"></i>
                <h3>${i18n.translate('auth.loginRequired')}</h3>
                <p>${i18n.translate('auth.loginToAccess')}</p>
                <button class="btn btn-primary" onclick="azsWebsite.showLoginModal()">
                    <i class="fas fa-sign-in-alt"></i> ${i18n.translate('auth.login')}
                </button>
            </div>
        `;
    }

    displayProfileData(user) {
        const profileName = document.getElementById('profileName');
        const profilePhone = document.getElementById('profilePhone');
        
        if (profileName) {
            profileName.textContent = user.name || i18n.translate('profile.user');
        }
        
        if (profilePhone) {
            profilePhone.textContent = user.phone || '';
        }
        
        // Обновляем баланс в шапке
        const userBalance = document.getElementById('userBalance');
        if (userBalance) {
            userBalance.textContent = `${(user.balance || 0).toFixed(2)} ${i18n.translate('profile.bonuses')}`;
        }
        
        // Отображаем данные в зависимости от активной вкладки
        this.switchProfileTab(this.currentProfileTab);
    }

    switchProfileTab(tab) {
        this.currentProfileTab = tab;
        
        // Обновляем активную вкладку
        document.querySelectorAll('.profile-menu li').forEach(item => {
            item.classList.remove('active');
        });
        
        const activeLink = document.querySelector(`.profile-menu a[data-tab="${tab}"]`);
        if (activeLink) {
            activeLink.parentElement.classList.add('active');
        }
        
        // Загружаем контент вкладки
        switch(tab) {
            case 'transactions':
                this.displayTransactionHistory();
                break;
            case 'bonuses':
                this.displayBonusesInfo();
                break;
            case 'settings':
                this.displaySettings();
                break;
            case 'profile':
            default:
                this.displayProfileInfo();
                break;
        }
    }

    displayProfileInfo() {
        const user = this.api.getCurrentUser();
        const tabContent = document.getElementById('profileTabContent');
        
        if (!tabContent) return;
        
        tabContent.innerHTML = `
            <div class="profile-info">
                <div class="info-card">
                    <h4>${i18n.translate('profile.fullName')}</h4>
                    <p>${user.name || '-'}</p>
                </div>
                <div class="info-card">
                    <h4>${i18n.translate('profile.phone')}</h4>
                    <p>${user.phone || '-'}</p>
                </div>
                <div class="info-card">
                    <h4>${i18n.translate('profile.balance')}</h4>
                    <p>${(user.balance || 0).toFixed(2)} BYN</p>
                </div>
                <div class="info-card">
                    <h4>${i18n.translate('profile.totalLiters')}</h4>
                    <p>${(user.total_liters || 0).toFixed(1)} л</p>
                </div>
                <div class="info-card">
                    <h4>${i18n.translate('profile.totalSpent')}</h4>
                    <p>${(user.total_spent || 0).toFixed(2)} BYN</p>
                </div>
                <div class="info-card">
                    <h4>${i18n.translate('profile.registrationDate')}</h4>
                    <p>${user.created_at ? new Date(user.created_at).toLocaleDateString() : '-'}</p>
                </div>
            </div>
        `;
    }

    async displayTransactionHistory() {
        const tabContent = document.getElementById('profileTabContent');
        if (!tabContent) return;
        
        tabContent.innerHTML = `
            <div class="loading">
                <i class="fas fa-spinner fa-spin"></i>
                <span>${i18n.translate('common.loading')}</span>
            </div>
        `;
        
        try {
            const response = await this.api.getTransactions();
            
            if (response.success && response.transactions && response.transactions.length > 0) {
                let tableHTML = `
                    <h3>${i18n.translate('profile.transactionHistory')}</h3>
                    <div class="table-container">
                        <table class="table">
                            <thead>
                                <tr>
                                    <th>${i18n.translate('profile.date')}</th>
                                    <th>${i18n.translate('profile.fuelType')}</th>
                                    <th>${i18n.translate('profile.amount')}</th>
                                    <th>${i18n.translate('profile.total')}</th>
                                    <th>${i18n.translate('profile.paymentMethod')}</th>
                                    <th>${i18n.translate('profile.status')}</th>
                                </tr>
                            </thead>
                            <tbody>
                `;
                
                response.transactions.forEach(trans => {
                    const statusClass = trans.status === 'Успешно' ? 'status-success' : 
                                       trans.status === 'В обработке' ? 'status-pending' : 'status-failed';
                    
                    tableHTML += `
                        <tr>
                            <td>${trans.created_at}</td>
                            <td>${trans.fuel_type}</td>
                            <td>${trans.liters} л</td>
                            <td>${trans.total_amount} BYN</td>
                            <td>${trans.payment_method}</td>
                            <td><span class="status-badge ${statusClass}">${trans.status}</span></td>
                        </tr>
                    `;
                });
                
                tableHTML += `
                            </tbody>
                        </table>
                    </div>
                `;
                
                tabContent.innerHTML = tableHTML;
            } else {
                tabContent.innerHTML = `
                    <div class="welcome-message">
                        <i class="fas fa-history fa-3x"></i>
                        <h3>${i18n.translate('profile.noTransactions')}</h3>
                        <p>${i18n.translate('profile.noTransactionsDesc')}</p>
                    </div>
                `;
            }
        } catch (error) {
            console.error('Failed to load transaction history:', error);
            tabContent.innerHTML = `
                <div class="welcome-message">
                    <i class="fas fa-exclamation-triangle fa-3x" style="color: var(--warning-color);"></i>
                    <h3>${i18n.translate('error')}</h3>
                    <p>${error.message || i18n.translate('notification.serverError')}</p>
                </div>
            `;
        }
    }

    displayBonusesInfo() {
        const user = this.api.getCurrentUser();
        const tabContent = document.getElementById('profileTabContent');
        
        if (!tabContent) return;
        
        tabContent.innerHTML = `
            <div class="bonuses-info">
                <div class="bonus-card">
                    <h3>${i18n.translate('profile.bonusBalance')}</h3>
                    <div class="bonus-amount">${(user.balance || 0).toFixed(2)} BYN</div>
                    <p>${i18n.translate('profile.bonusDescription')}</p>
                </div>
                
                <h3>${i18n.translate('profile.bonusRules')}</h3>
                <div class="bonus-rules">
                    <div class="rule">
                        <i class="fas fa-percentage" style="color: var(--success-color);"></i>
                        <div>
                            <h4>${i18n.translate('profile.rule1')}</h4>
                            <p>${i18n.translate('profile.rule1desc')}</p>
                        </div>
                    </div>
                    <div class="rule">
                        <i class="fas fa-exchange-alt" style="color: var(--primary-color);"></i>
                        <div>
                            <h4>${i18n.translate('profile.rule2')}</h4>
                            <p>${i18n.translate('profile.rule2desc')}</p>
                        </div>
                    </div>
                    <div class="rule">
                        <i class="fas fa-infinity" style="color: var(--warning-color);"></i>
                        <div>
                            <h4>${i18n.translate('profile.rule3')}</h4>
                            <p>${i18n.translate('profile.rule3desc')}</p>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    displaySettings() {
        const user = this.api.getCurrentUser();
        const tabContent = document.getElementById('profileTabContent');
        
        if (!tabContent) return;
        
        tabContent.innerHTML = `
            <div class="settings">
                <h3>${i18n.translate('profile.settingsTitle')}</h3>
                
                <div class="settings-section">
                    <h4>${i18n.translate('profile.personalData')}</h4>
                    <form id="profileForm" onsubmit="return false;">
                        <div class="form-group">
                            <label for="editName">${i18n.translate('profile.fullName')}</label>
                            <input type="text" id="editName" value="${user.name || ''}">
                        </div>
                        <div class="form-group">
                            <label for="editPhone">${i18n.translate('profile.phone')}</label>
                            <input type="tel" id="editPhone" class="phone-input" value="${user.phone || ''}">
                        </div>
                        <button class="btn btn-primary" onclick="azsWebsite.updateProfile()">
                            <i class="fas fa-save"></i> ${i18n.translate('profile.saveChanges')}
                        </button>
                    </form>
                </div>
                
                <div class="settings-section">
                    <h4>${i18n.translate('profile.security')}</h4>
                    <form id="passwordForm" onsubmit="return false;">
                        <div class="form-group">
                            <label for="currentPassword">${i18n.translate('profile.currentPassword')}</label>
                            <input type="password" id="currentPassword">
                        </div>
                        <div class="form-group">
                            <label for="newPassword">${i18n.translate('profile.newPassword')}</label>
                            <input type="password" id="newPassword">
                        </div>
                        <div class="form-group">
                            <label for="confirmNewPassword">${i18n.translate('auth.confirmPassword')}</label>
                            <input type="password" id="confirmNewPassword">
                        </div>
                        <button class="btn btn-primary" onclick="azsWebsite.changePassword()">
                            <i class="fas fa-key"></i> ${i18n.translate('profile.changePassword')}
                        </button>
                    </form>
                </div>
                
                <div class="settings-section" style="border-color: var(--accent-color);">
                    <h4 style="color: var(--accent-color);">${i18n.translate('profile.dangerZone')}</h4>
                    <p style="margin-bottom: 15px;">${i18n.translate('profile.deleteWarning')}</p>
                    <button class="btn" style="background-color: var(--accent-color); color: white;" 
                            onclick="azsWebsite.deleteAccount()">
                        <i class="fas fa-trash"></i> ${i18n.translate('profile.deleteAccount')}
                    </button>
                </div>
            </div>
        `;
        
        // Инициализируем телефонный инпут для настроек
        const phoneInput = document.getElementById('editPhone');
        if (phoneInput) {
            window.intlTelInput(phoneInput, {
                initialCountry: "by",
                preferredCountries: ["by", "ru", "ua"],
                separateDialCode: true,
                utilsScript: "https://cdn.jsdelivr.net/npm/intl-tel-input@19.2.16/build/js/utils.js"
            });
        }
    }

    async updateProfile() {
        const name = document.getElementById('editName')?.value;
        const phoneInput = document.getElementById('editPhone');
        const phone = phoneInput ? phoneInput.value : '';
        
        if (!name || !phone) {
            this.showNotification('Заполните все поля', 'error');
            return;
        }
        
        try {
            const updateData = {
                name: name,
                phone: phone
            };
            
            const response = await this.api.updateProfile(updateData);
            
            this.showNotification(i18n.translate('notification.profileUpdated'), 'success');
            this.loadProfileData();
            
        } catch (error) {
            this.showNotification(error.message || 'Ошибка обновления профиля', 'error');
        }
    }

    async changePassword() {
        const currentPassword = document.getElementById('currentPassword')?.value;
        const newPassword = document.getElementById('newPassword')?.value;
        const confirmPassword = document.getElementById('confirmNewPassword')?.value;
        
        if (!currentPassword || !newPassword || !confirmPassword) {
            this.showNotification('Заполните все поля', 'error');
            return;
        }
        
        if (newPassword !== confirmPassword) {
            this.showNotification('Пароли не совпадают', 'error');
            return;
        }
        
        if (newPassword.length < 6) {
            this.showNotification('Пароль должен содержать минимум 6 символов', 'error');
            return;
        }
        
        try {
            const updateData = {
                password: newPassword
            };
            
            const response = await this.api.updateProfile(updateData);
            
            this.showNotification('Пароль успешно изменен', 'success');
            document.getElementById('passwordForm')?.reset();
            
        } catch (error) {
            this.showNotification(error.message || 'Ошибка смены пароля', 'error');
        }
    }

    deleteAccount() {
        if (confirm('Вы уверены, что хотите удалить аккаунт? Это действие необратимо.')) {
            this.showNotification('Функция удаления аккаунта в разработке', 'warning');
        }
    }

    async generateQrCode() {
        if (!this.api.isAuthenticated()) {
            this.showLoginModal();
            return;
        }
        
        const azsId = parseInt(document.getElementById('azsIdInput')?.value || '1');
        const nozzleNumber = parseInt(document.getElementById('nozzleInput')?.value || '1');
        
        if (azsId < 1 || nozzleNumber < 1 || nozzleNumber > 4) {
            this.showNotification('Введите корректные данные АЗС и колонки', 'error');
            return;
        }
        
        try {
            const response = await this.api.generateQrCode(azsId, nozzleNumber);
            
            if (response.success) {
                // Генерируем QR-код на клиенте
                this.displayQrCode(response.qr_data);
                this.showNotification(i18n.translate('notification.qrGenerated'), 'success');
            }
        } catch (error) {
            console.error('Failed to generate QR code:', error);
            this.showNotification(error.message || 'Ошибка генерации QR-кода', 'error');
        }
    }

    displayQrCode(qrData) {
        const qrContainer = document.getElementById('qrCodeDisplay');
        if (!qrContainer) return;
        
        qrContainer.innerHTML = '';
        
        // Создаем canvas для QR-кода
        const canvas = document.createElement('canvas');
        qrContainer.appendChild(canvas);
        
        // Генерируем QR-код
        QRCode.toCanvas(canvas, qrData, {
            width: 250,
            margin: 1,
            color: {
                dark: '#2c3e50',
                light: '#ecf0f1'
            }
        }, (error) => {
            if (error) {
                console.error('QR generation error:', error);
                qrContainer.innerHTML = `
                    <div class="qr-placeholder">
                        <i class="fas fa-exclamation-triangle" style="color: var(--accent-color);"></i>
                        <p>Ошибка генерации QR-кода</p>
                    </div>
                `;
            }
        });
    }

    showQrScanner() {
        this.closeAllModals();
        document.getElementById('qrScannerModal').style.display = 'flex';
    }

    toggleCamera() {
        if (this.cameraActive) {
            this.stopCamera();
        } else {
            this.startCamera();
        }
    }

    async startCamera() {
        const scanner = document.getElementById('qrScanner');
        if (!scanner) return;
        
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ 
                video: { facingMode: 'environment' } 
            });
            
            const video = document.createElement('video');
            video.srcObject = stream;
            video.setAttribute('playsinline', 'true');
            video.style.width = '100%';
            video.style.height = '100%';
            
            scanner.innerHTML = '';
            scanner.appendChild(video);
            
            await video.play();
            
            this.cameraActive = true;
            this.cameraStream = stream;
            this.cameraVideo = video;
            
            // Обновляем кнопку
            const toggleBtn = document.getElementById('toggleCameraBtn');
            if (toggleBtn) {
                toggleBtn.innerHTML = `<i class="fas fa-video-slash"></i> ${i18n.translate('payment.toggleCamera')}`;
            }
            
            // TODO: Добавить распознавание QR-кода с видео
            
        } catch (error) {
            console.error('Camera error:', error);
            this.showNotification('Ошибка доступа к камере', 'error');
        }
    }

    stopCamera() {
        if (this.cameraStream) {
            this.cameraStream.getTracks().forEach(track => track.stop());
            this.cameraStream = null;
        }
        
        if (this.cameraVideo) {
            this.cameraVideo.remove();
            this.cameraVideo = null;
        }
        
        const scanner = document.getElementById('qrScanner');
        if (scanner) {
            scanner.innerHTML = `
                <div class="scanner-placeholder">
                    <i class="fas fa-camera"></i>
                    <p>${i18n.translate('payment.scannerPlaceholder')}</p>
                </div>
            `;
        }
        
        this.cameraActive = false;
        
        // Обновляем кнопку
        const toggleBtn = document.getElementById('toggleCameraBtn');
        if (toggleBtn) {
            toggleBtn.innerHTML = `<i class="fas fa-video"></i> ${i18n.translate('payment.toggleCamera')}`;
        }
    }

    switchCamera() {
        if (this.cameraActive) {
            this.stopCamera();
            setTimeout(() => this.startCamera(), 100);
        }
    }

    processManualQrCode() {
        const manualInput = document.getElementById('manualQrInput');
        const qrCode = manualInput?.value.trim();
        
        if (!qrCode) {
            this.showNotification('Введите QR-код', 'error');
            return;
        }
        
        // Парсим QR-код (формат: azs:1:nozzle:2:user:123:time:...)
        const parts = qrCode.split(':');
        
        if (parts.length >= 6 && parts[0] === 'azs') {
            const azsId = parseInt(parts[1]);
            const nozzleNumber = parseInt(parts[3]);
            
            if (azsId && nozzleNumber) {
                this.startRefueling(azsId, nozzleNumber);
                this.closeAllModals();
                return;
            }
        }
        
        this.showNotification('Неверный формат QR-кода', 'error');
    }

    startRefueling(azsId = 1, nozzleNumber = 1) {
        if (!this.api.isAuthenticated()) {
            this.showLoginModal();
            return;
        }
        
        this.showPaymentModal(azsId, nozzleNumber);
    }

    showPaymentModal(azsId, nozzleNumber) {
    const modal = document.getElementById('paymentModal');
    if (!modal) return;
    
    const content = document.getElementById('paymentContent');
    if (!content) return;
    
    const user = this.api.getCurrentUser();
    const userBalance = user?.balance || 0;
    const maxBonusUsage = 0.5; // Можно использовать до 50% бонусами
    
    // Загружаем данные для оплаты
    content.innerHTML = `
        <div class="payment-step" id="step1">
            <div class="payment-header">
                <h3>${i18n.translate('payment.payment')}</h3>
                <p>АЗС: ${azsId}, Колонка: ${nozzleNumber}</p>
            </div>
            
            <div class="form-group">
                <label for="fuelTypeSelect">${i18n.translate('profile.fuelType')}</label>
                <select id="fuelTypeSelect" class="form-control">
                    <option value="ai95">${i18n.translate('fuel.ai95')}</option>
                    <option value="ai92">${i18n.translate('fuel.ai92')}</option>
                    <option value="ai98">${i18n.translate('fuel.ai98')}</option>
                    <option value="ai100">${i18n.translate('fuel.ai100')}</option>
                    <option value="dt">${i18n.translate('fuel.diesel')}</option>
                    <option value="dtk5">${i18n.translate('fuel.dtk5')}</option>
                </select>
            </div>
            
            <div class="form-group">
                <label for="fuelAmount">${i18n.translate('profile.amount')} (литров):</label>
                <input type="number" id="fuelAmount" min="1" max="100" step="0.1" value="10" class="form-control">
            </div>
            
            <div class="bonus-section">
                <div class="bonus-info">
                    <span>${i18n.translate('payment.availableBonuses')}: <strong>${userBalance.toFixed(2)} BYN</strong></span>
                    <small>${i18n.translate('payment.maxBonuses')} ${(maxBonusUsage * 100)}${i18n.translate('payment.bonusesPercent')}</small>
                </div>
                
                <div class="form-group">
                    <label for="bonusAmount">${i18n.translate('payment.bonusesToSpend')} (BYN):</label>
                    <input type="number" id="bonusAmount" min="0" max="${Math.min(userBalance, 100)}" step="0.01" value="0" class="form-control">
                    <div class="bonus-slider-container">
                        <input type="range" id="bonusSlider" min="0" max="100" value="0" class="bonus-slider">
                        <div class="slider-labels">
                            <span>0%</span>
                            <span>25%</span>
                            <span>50%</span>
                            <span>75%</span>
                            <span>100%</span>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="form-group">
                <label>${i18n.translate('payment.method')}:</label>
                <div class="payment-method-card">
                    <i class="fas fa-credit-card"></i>
                    <span>${i18n.translate('payment.cardOnly')}</span>
                </div>
            </div>
            
            <div class="payment-summary" id="paymentSummary">
                <!-- Сводка будет рассчитана динамически -->
            </div>
            
            <div class="payment-actions">
                <button class="btn btn-outline" onclick="azsWebsite.closePaymentModal()">
                    <i class="fas fa-times"></i> ${i18n.translate('common.cancel')}
                </button>
                <button class="btn btn-primary" onclick="azsWebsite.processPayment(${azsId}, ${nozzleNumber})">
                    <i class="fas fa-check"></i> ${i18n.translate('payment.continue')}
                </button>
            </div>
        </div>
    `;
    
    modal.style.display = 'flex';
    
    // Инициализируем слайдер бонусов
    this.setupBonusSlider(userBalance, maxBonusUsage);
    
    // Обновляем сводку при изменении параметров
    ['fuelAmount', 'bonusAmount', 'bonusSlider', 'fuelTypeSelect'].forEach(id => {
        document.getElementById(id)?.addEventListener('change', () => {
            this.updatePaymentSummary(userBalance, maxBonusUsage);
        });
        document.getElementById(id)?.addEventListener('input', () => {
            this.updatePaymentSummary(userBalance, maxBonusUsage);
        });
    });
    
    // Инициализируем сводку
    this.updatePaymentSummary(userBalance, maxBonusUsage);
}

    setupBonusSlider(userBalance, maxBonusUsage) {
    const bonusAmountInput = document.getElementById('bonusAmount');
    const bonusSlider = document.getElementById('bonusSlider');
    
    if (!bonusAmountInput || !bonusSlider) return;
    
    // Связываем слайдер и инпут
    bonusSlider.addEventListener('input', () => {
        const sliderValue = parseInt(bonusSlider.value);
        const maxBonus = userBalance;
        const bonusValue = (sliderValue / 100) * maxBonus;
        
        bonusAmountInput.value = bonusValue.toFixed(2);
        this.updatePaymentSummary(userBalance, maxBonusUsage);
    });
    
    bonusAmountInput.addEventListener('input', () => {
        const bonusValue = parseFloat(bonusAmountInput.value) || 0;
        const maxBonus = userBalance;
        const sliderValue = (bonusValue / maxBonus) * 100;
        
        bonusSlider.value = Math.min(sliderValue, 100);
        this.updatePaymentSummary(userBalance, maxBonusUsage);
    });
}

    updatePaymentSummary(userBalance = 0, maxBonusUsage = 0.5) {
    const container = document.getElementById('paymentSummary');
    if (!container) return;
    
    const fuelType = document.getElementById('fuelTypeSelect')?.value || 'ai95';
    const amount = parseFloat(document.getElementById('fuelAmount')?.value || 10);
    const bonusAmount = parseFloat(document.getElementById('bonusAmount')?.value || 0);
    
    // Получаем цену
    const fuelPrices = {
        ai92: 2.50,
        ai95: 2.80,
        ai98: 3.10,
        ai100: 3.50,
        dt: 2.90,
        dtk5: 3.00
    };
    
    const pricePerLiter = fuelPrices[fuelType] || 2.80;
    const totalAmount = pricePerLiter * amount;
    
    // Ограничиваем бонусы: не больше 50% от суммы и не больше доступного баланса
    const maxAllowedBonus = Math.min(userBalance, totalAmount * maxBonusUsage);
    const actualBonus = Math.min(bonusAmount, maxAllowedBonus);
    
    const cardAmount = totalAmount - actualBonus;
    const bonusEarned = totalAmount * 0.01; // 1% бонусов
    
    // Получаем названия типов топлива
    const fuelTypeNames = {
        ai92: i18n.translate('fuel.ai92'),
        ai95: i18n.translate('fuel.ai95'),
        ai98: i18n.translate('fuel.ai98'),
        ai100: i18n.translate('fuel.ai100'),
        dt: i18n.translate('fuel.diesel'),
        dtk5: i18n.translate('fuel.dtk5')
    };
    
    container.innerHTML = `
        <h4>${i18n.translate('payment.summary')}</h4>
        <div class="summary-card">
            <div class="summary-row">
                <span>${i18n.translate('profile.fuelType')}:</span>
                <span>${fuelTypeNames[fuelType] || fuelType}</span>
            </div>
            <div class="summary-row">
                <span>${i18n.translate('profile.amount')}:</span>
                <span>${amount.toFixed(1)} л</span>
            </div>
            <div class="summary-row">
                <span>${i18n.translate('profile.price')}:</span>
                <span>${pricePerLiter.toFixed(2)} BYN/л</span>
            </div>
            <div class="summary-divider"></div>
            <div class="summary-row total">
                <span>${i18n.translate('profile.total')}:</span>
                <span>${totalAmount.toFixed(2)} BYN</span>
            </div>
            <div class="summary-row">
                <span>${i18n.translate('payment.method')}:</span>
                <span>${i18n.translate('payment.cardOnly')}</span>
            </div>
            ${actualBonus > 0 ? `
            <div class="summary-row">
                <span>${i18n.translate('payment.bonusUsed')}:</span>
                <span style="color: var(--success-color);">-${actualBonus.toFixed(2)} BYN</span>
            </div>
            ` : ''}
            ${cardAmount > 0 ? `
            <div class="summary-row">
                <span>${i18n.translate('payment.toPay')}:</span>
                <span style="font-weight: bold;">${cardAmount.toFixed(2)} BYN</span>
            </div>
            ` : `
            <div class="summary-row">
                <span>${i18n.translate('payment.toPay')}:</span>
                <span style="font-weight: bold; color: var(--success-color);">0.00 BYN (оплачено бонусами)</span>
            </div>
            `}
            <div class="summary-row">
                <span>${i18n.translate('payment.bonusEarned')}:</span>
                <span style="color: var(--primary-color);">+${bonusEarned.toFixed(2)} BYN</span>
            </div>
        </div>
    `;
}

    getPaymentMethodName(method) {
        const names = {
            cash: i18n.translate('payment.cash'),
            card: i18n.translate('payment.card'),
            bonus: i18n.translate('payment.bonus'),
            mixed: i18n.translate('payment.mixed')
        };
        return names[method] || method;
    }

    async processPayment(azsId, nozzleNumber) {
    const fuelType = document.getElementById('fuelTypeSelect')?.value || 'ai95';
    const amount = parseFloat(document.getElementById('fuelAmount')?.value || 10);
    const bonusAmount = parseFloat(document.getElementById('bonusAmount')?.value || 0);
    
    // Получаем названия типов топлива
    const fuelTypeNames = {
        ai92: i18n.translate('fuel.ai92'),
        ai95: i18n.translate('fuel.ai95'),
        ai98: i18n.translate('fuel.ai98'),
        ai100: i18n.translate('fuel.ai100'),
        dt: i18n.translate('fuel.diesel'),
        dtk5: i18n.translate('fuel.dtk5')
    };
    
    // Получаем цены
    const fuelPrices = {
        ai92: 2.50,
        ai95: 2.80,
        ai98: 3.10,
        ai100: 3.50,
        dt: 2.90,
        dtk5: 3.00
    };
    
    const pricePerLiter = fuelPrices[fuelType] || 2.80;
    const totalAmount = pricePerLiter * amount;
    
    const user = this.api.getCurrentUser();
    const userBalance = user?.balance || 0;
    
    // Проверяем корректность бонусов
    const maxBonusUsage = 0.5; // 50%
    const maxAllowedBonus = Math.min(userBalance, totalAmount * maxBonusUsage);
    const actualBonus = Math.min(bonusAmount, maxAllowedBonus);
    const cardAmount = totalAmount - actualBonus;
    
    try {
        // Создаем транзакцию
        const transactionData = {
            fuel_type: fuelTypeNames[fuelType] || fuelType,
            liters: amount,
            price_per_liter: pricePerLiter,
            total_amount: totalAmount,
            cash_in: cardAmount,
            change: 0,
            bonus_spent: actualBonus,
            payment_method: i18n.translate('payment.cardOnly'),
            azs_id: azsId,
            nozzle: nozzleNumber,
            user_id: user.id,
            user_name: user.name || 'Гость',
            status: 'Успешно',
            created_at: new Date().toISOString()
        };
        
        console.log('📤 Отправка транзакции:', transactionData); // Для отладки
        
        const transactionResponse = await this.api.createTransaction(transactionData);
        
        console.log('📤 Ответ от сервера:', transactionResponse); // Для отладки
        
        if (transactionResponse.success) {
            // Обновляем баланс пользователя
            const bonusEarned = totalAmount * 0.01;
            user.balance = (user.balance || 0) - actualBonus + bonusEarned;
            user.total_spent = (user.total_spent || 0) + totalAmount;
            user.total_liters = (user.total_liters || 0) + amount;
            
            this.api.setUserData(user);
            
            // Добавляем дополнительные данные для чека
            transactionResponse.fuel_type = transactionData.fuel_type;
            transactionResponse.liters = transactionData.liters;
            transactionResponse.price_per_liter = transactionData.price_per_liter;
            transactionResponse.total_amount = transactionData.total_amount;
            transactionResponse.payment_method = transactionData.payment_method;
            transactionResponse.bonus_spent = transactionData.bonus_spent;
            transactionResponse.bonus_earned = bonusEarned;
            
            // Показываем успешное сообщение
            this.showPaymentSuccess(transactionResponse);
            
        } else {
            throw new Error(transactionResponse.message || 'Ошибка создания транзакции');
        }
        
    } catch (error) {
        console.error('Payment processing error:', error);
        this.showNotification(error.message || i18n.translate('notification.paymentError'), 'error');
    }
}

    showPaymentSuccess(transactionResponse) {
    const content = document.getElementById('paymentContent');
    if (!content) return;
    
    content.innerHTML = `
        <div class="payment-success">
            <div class="success-icon">
                <i class="fas fa-check-circle"></i>
            </div>
            <h3>${i18n.translate('notification.paymentSuccess')}</h3>
            <p>${i18n.translate('payment.transactionId')}: ${transactionResponse.id || transactionResponse.transaction_id || 'N/A'}</p>
            <p>${i18n.translate('payment.canStartRefuel')}</p>
            
            <div class="success-actions">
                <button class="btn btn-primary" onclick="azsWebsite.generateReceipt(${transactionResponse.id || transactionResponse.transaction_id || 0})">
                    <i class="fas fa-receipt"></i> ${i18n.translate('payment.getReceipt')}
                </button>
                <button class="btn btn-outline" onclick="azsWebsite.closePaymentModal()">
                    <i class="fas fa-times"></i> ${i18n.translate('common.close')}
                </button>
            </div>
        </div>
    `;
}

    async generateReceipt(transactionId) {
    try {
        // Получаем данные текущей транзакции
        const user = this.api.getCurrentUser();
        const fuelType = document.getElementById('fuelTypeSelect')?.value || 'ai95';
        const amount = parseFloat(document.getElementById('fuelAmount')?.value || 10);
        const bonusAmount = parseFloat(document.getElementById('bonusAmount')?.value || 0);
        
        const fuelPrices = {
            ai92: 2.50,
            ai95: 2.80,
            ai98: 3.10,
            ai100: 3.50,
            dt: 2.90,
            dtk5: 3.00
        };
        
        const pricePerLiter = fuelPrices[fuelType] || 2.80;
        const totalAmount = pricePerLiter * amount;
        const bonusEarned = totalAmount * 0.01;
        
        const receiptData = {
            id: transactionId,
            fuel_type: i18n.translate(`fuel.${fuelType}`),
            liters: amount,
            price_per_liter: pricePerLiter,
            total_amount: totalAmount,
            payment_method: i18n.translate('payment.cardOnly'),
            bonus_spent: bonusAmount,
            bonus_earned: bonusEarned,
            user_id: user.id,
            user_name: user.name || 'Гость',
            created_at: new Date().toISOString()
        };
        
        const response = await this.api.generateReceipt(receiptData);
        
        if (response.success && response.receipt) {
            this.showReceipt(response.receipt);
        } else {
            // Если сервер не вернул чек, создаем его локально
            this.showReceipt(this.createLocalReceipt(receiptData));
        }
    } catch (error) {
        console.error('Failed to generate receipt:', error);
        // Создаем локальный чек в случае ошибки
        this.showReceipt(this.createLocalReceipt({
            id: transactionId,
            fuel_type: i18n.translate('fuel.ai95'),
            liters: 10,
            price_per_liter: 2.80,
            total_amount: 28.00,
            payment_method: i18n.translate('payment.cardOnly'),
            bonus_spent: 0,
            bonus_earned: 0.28,
            created_at: new Date().toISOString()
        }));
    }
}

// Добавьте метод для создания локального чека
createLocalReceipt(transactionData) {
    // Генерируем номер чека
    const date = new Date();
    const receiptNumber = `R-${date.getFullYear()}${(date.getMonth() + 1).toString().padStart(2, '0')}${date.getDate().toString().padStart(2, '0')}-${Math.floor(Math.random() * 1000).toString().padStart(4, '0')}`;
    
    return {
        receipt_number: receiptNumber,
        transaction_id: transactionData.id || 0,
        fuel_type: transactionData.fuel_type || 'Не указано',
        liters: transactionData.liters || 0,
        price_per_liter: transactionData.price_per_liter || 0,
        total_amount: transactionData.total_amount || 0,
        payment_method: transactionData.payment_method || 'Не указано',
        bonus_spent: transactionData.bonus_spent || 0,
        bonus_earned: transactionData.bonus_earned || 0,
        created_at: transactionData.created_at || new Date().toISOString(),
        qr_code_data: `AZS-RECEIPT:${receiptNumber}:${transactionData.id || 0}:${Date.now()}`
    };
}

    showReceipt(receipt) {
    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.style.display = 'flex';
    
    modal.innerHTML = `
        <div class="modal-content" style="max-width: 400px;">
            <span class="close" onclick="this.parentElement.parentElement.remove()">&times;</span>
            <div class="receipt">
                <h3>АЗС PHAETON</h3>
                <div class="receipt-line">========================</div>
                <div class="receipt-row">
                    <span>${i18n.translate('payment.receiptNumber')}:</span>
                    <span>${receipt.receipt_number || 'N/A'}</span>
                </div>
                <div class="receipt-row">
                    <span>${i18n.translate('profile.date')}:</span>
                    <span>${new Date(receipt.created_at).toLocaleString()}</span>
                </div>
                <div class="receipt-line">------------------------</div>
                <div class="receipt-row">
                    <span>${i18n.translate('profile.fuelType')}:</span>
                    <span>${receipt.fuel_type}</span>
                </div>
                <div class="receipt-row">
                    <span>${i18n.translate('profile.amount')}:</span>
                    <span>${receipt.liters} л</span>
                </div>
                <div class="receipt-row">
                    <span>${i18n.translate('profile.price')}:</span>
                    <span>${receipt.price_per_liter} BYN/л</span>
                </div>
                <div class="receipt-line">------------------------</div>
                <div class="receipt-row total">
                    <span>${i18n.translate('profile.total')}:</span>
                    <span>${receipt.total_amount} BYN</span>
                </div>
                <div class="receipt-row">
                    <span>${i18n.translate('profile.paymentMethod')}:</span>
                    <span>${receipt.payment_method}</span>
                </div>
                ${receipt.bonus_spent > 0 ? `
                <div class="receipt-row">
                    <span>${i18n.translate('payment.bonusUsed')}:</span>
                    <span>${receipt.bonus_spent} BYN</span>
                </div>
                ` : ''}
                <div class="receipt-row">
                    <span>${i18n.translate('payment.bonusEarned')}:</span>
                    <span>${receipt.bonus_earned} BYN</span>
                </div>
                <div class="receipt-line">========================</div>
                <p class="receipt-thanks">${i18n.translate('payment.thankYou')}</p>
                <div class="receipt-qr">
                    <p>${i18n.translate('payment.qrForVerification')}</p>
                    <small>${receipt.qr_code_data || 'N/A'}</small>
                </div>
            </div>
            <div class="receipt-actions">
                <button class="btn btn-primary" onclick="window.print()">
                    <i class="fas fa-print"></i> ${i18n.translate('payment.print')}
                </button>
                <button class="btn btn-outline" onclick="this.closest('.modal').remove()">
                    <i class="fas fa-times"></i> ${i18n.translate('common.close')}
                </button>
            </div>
        </div>
    `;
    
    document.body.appendChild(modal);
    
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.remove();
        }
    });
}

    closePaymentModal() {
        const modal = document.getElementById('paymentModal');
        if (modal) {
            modal.style.display = 'none';
        }
    }

    showNotification(message, type = 'info') {
        // Удаляем предыдущие уведомления
        const existingNotifications = document.querySelectorAll('.notification');
        existingNotifications.forEach(notification => {
            if (notification.parentElement) {
                notification.parentElement.remove();
            }
        });
        
        // Создаем контейнер для уведомлений если его нет
        let container = document.getElementById('notificationContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'notificationContainer';
            document.body.appendChild(container);
        }
        
        // Создаем новое уведомление
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        
        const icons = {
            success: 'fa-check-circle',
            error: 'fa-exclamation-circle',
            warning: 'fa-exclamation-triangle',
            info: 'fa-info-circle'
        };
        
        notification.innerHTML = `
            <i class="fas ${icons[type] || 'fa-info-circle'}"></i>
            <span>${message}</span>
            <span class="notification-close" onclick="this.parentElement.remove()">
                <i class="fas fa-times"></i>
            </span>
        `;
        
        container.appendChild(notification);
        
        // Автоматическое удаление через 5 секунд
        setTimeout(() => {
            if (notification.parentElement) {
                notification.remove();
            }
        }, 5000);
    }
}

// Инициализация сайта при загрузке страницы
window.addEventListener('DOMContentLoaded', () => {
    window.azsWebsite = new AzsWebsite();
});