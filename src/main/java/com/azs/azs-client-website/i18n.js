class Internationalization {
    constructor() {
        this.currentLang = localStorage.getItem('language') || 'ru';
        this.translations = {
            ru: {
                // Навигация
                'nav.home': 'Главная',
                'nav.fuel': 'Топливо',
                'nav.payment': 'Оплата QR',
                'nav.stations': 'АЗС',
                'nav.profile': 'Профиль',

                'notification.paymentSuccess': 'Оплата успешно завершена!',
                'payment.transactionId': 'Номер транзакции',
                'payment.canStartRefuel': 'Теперь можно приступать к заправке',
                'payment.getReceipt': 'Получить чек',
                'payment.receiptNumber': 'Номер чека',
                'payment.thankYou': 'Спасибо за покупку!',
                'payment.qrForVerification': 'QR-код для проверки',
                'payment.print': 'Печать',
                
                // Добавьте эти если их нет
                'common.close': 'Закрыть',

                'payment.method': 'Способ оплаты',
                'payment.cardOnly': 'Банковская карта',
                'payment.useBonuses': 'Использовать бонусы',
                'payment.availableBonuses': 'Доступно бонусов',
                'payment.bonusesToSpend': 'Бонусов для списания',
                'payment.maxBonuses': 'Можно использовать до',
                'payment.bonusesPercent': '% от суммы',
                
                'stations.nozzles': 'Колонок:',
                'profile.bonusBalance': 'Бонусный баланс',
                'profile.bonusDescription': 'Используйте бонусы для оплаты до 50% от суммы покупки',
                'payment.summary': 'Сводка',
                'payment.toPay': 'К оплате картой',
                'payment.bonusEarned': 'Бонусов будет начислено',
                
                // Также добавьте перевод для 'cards' если его нет
                'payment.cards': 'Банковские карты',
                
                // Аутентификация
                'auth.login': 'Войти',
                'auth.register': 'Регистрация',
                'auth.logout': 'Выйти',
                'auth.phone': 'Номер телефона',
                'auth.password': 'Пароль',
                'auth.confirmPassword': 'Подтвердите пароль',
                'auth.fullName': 'ФИО',
                'auth.remember': 'Запомнить меня',
                'auth.forgot': 'Забыли пароль?',
                'auth.noAccount': 'Нет аккаунта?',
                'auth.haveAccount': 'Уже есть аккаунт?',
                'auth.acceptTerms': 'Я принимаю',
                'auth.passwordHint': 'Минимум 6 символов',
                
                // Герой
                'hero.title': 'Заправка стала проще',
                'hero.subtitle': 'Быстрая оплата QR-кодом, бонусная система и удобный сервис',
                'hero.fuelTypes': 'Видов топлива',
                'hero.stations': 'АЗС в сети',
                'hero.customers': 'Довольных клиентов',
                'hero.startRefuel': 'Начать заправку',
                'hero.learnMore': 'Узнать больше',
                'payment.bonusUsed': 'Бонусов к списанию: ',
                
                // Топливо
                'fuel.title': 'Цены на топливо',
                'fuel.ai92': 'АИ-92',
                'fuel.ai95': 'АИ-95',
                'fuel.ai98': 'АИ-98',
                'fuel.ai100': 'АИ-100',
                'fuel.diesel': 'Дизель',
                'fuel.dtk5': 'ДТ-К5',
                'fuel.perLiter': 'BYN/литр',
                'fuel.available': 'В наличии',
                'fuel.low': 'Мало',
                'fuel.unavailable': 'Нет в наличии',
                
                // Оплата
                'payment.title': 'Оплата QR-кодом',
                'payment.scanCode': 'Отсканируйте код на колонке',
                'payment.generateQr': 'Сгенерировать QR',
                'payment.scan': 'Сканировать QR',
                'payment.howItWorks': 'Как это работает:',
                'payment.step1': 'Подойдите к колонке АЗС',
                'payment.step2': 'Отсканируйте QR-код на колонке',
                'payment.step3': 'Выберите тип и количество топлива',
                'payment.step4': 'Оплатите удобным способом',
                'payment.step5': 'Начните заправку',
                'payment.payment': 'Оплата заправки',
                'payment.scanQr': 'Сканирование QR-кода',
                'payment.switchCamera': 'Сменить камеру',
                'payment.toggleCamera': 'Включить камеру',
                'payment.orEnterCode': 'Или введите код вручную:',
                'payment.continue': 'Продолжить',
                'payment.scannerPlaceholder': 'Для сканирования QR-кода используйте камеру устройства',
                
                // АЗС
                'stations.title': 'Наши АЗС на карте',
                'stations.map': 'Карта АЗС',
                'stations.selectStation': 'Выберите АЗС из списка',
                'stations.list': 'Список АЗС',
                
                // Профиль
                'profile.title': 'Личный кабинет',
                'profile.profile': 'Профиль',
                'profile.history': 'История',
                'profile.bonuses': 'Бонусы',
                'profile.settings': 'Настройки',
                'profile.welcome': 'Добро пожаловать!',
                'profile.selectTab': 'Выберите раздел в меню слева',
                'profile.fullName': 'ФИО',
                'profile.phone': 'Телефон',
                'profile.balance': 'Баланс бонусов',
                'profile.totalLiters': 'Всего заправлено',
                'profile.totalSpent': 'Всего потрачено',
                'profile.registrationDate': 'Дата регистрации',
                'profile.transactionHistory': 'История транзакций',
                'profile.noTransactions': 'Нет транзакций',
                'profile.date': 'Дата',
                'profile.fuelType': 'Тип топлива',
                'profile.amount': 'Количество',
                'profile.price': 'Цена',
                'profile.total': 'Сумма',
                'profile.paymentMethod': 'Способ оплаты',
                'profile.status': 'Статус',
                'profile.bonusRules': 'Правила бонусной системы',
                'profile.rule1': '1% от каждой покупки',
                'profile.rule1desc': 'За каждую заправку вы получаете 1% от суммы в бонусах',
                'profile.rule2': 'Оплата бонусами',
                'profile.rule2desc': 'Можно оплатить до 50% от суммы покупки бонусами',
                'profile.rule3': 'Без срока действия',
                'profile.rule3desc': 'Бонусы не сгорают и действуют бессрочно',
                'profile.settingsTitle': 'Настройки аккаунта',
                'profile.personalData': 'Личные данные',
                'profile.security': 'Безопасность',
                'profile.currentPassword': 'Текущий пароль',
                'profile.newPassword': 'Новый пароль',
                'profile.dangerZone': 'Опасная зона',
                'profile.deleteWarning': 'Удаление аккаунта невозможно отменить',
                'profile.deleteAccount': 'Удалить аккаунт',
                'profile.saveChanges': 'Сохранить изменения',
                'profile.changePassword': 'Сменить пароль',
                
                // Общие
                'common.loading': 'Загрузка...',
                'common.save': 'Сохранить',
                'common.cancel': 'Отмена',
                'common.confirm': 'Подтвердить',
                'common.back': 'Назад',
                'common.next': 'Далее',
                'common.yes': 'Да',
                'common.no': 'Нет',
                'success': 'Успешно',
                'error': 'Ошибка',
                'warning': 'Внимание',
                'info': 'Информация',
                
                // Футер
                'footer.slogan': 'Качество топлива и сервиса на высшем уровне',
                'footer.contacts': 'Контакты',
                'footer.24h': 'Круглосуточно',
                'footer.address': 'Минск, ул. Топливная, 1',
                'footer.download': 'Скачайте приложение',
                'footer.rights': 'Все права защищены.',
                'footer.privacy': 'Политика конфиденциальности',
                'footer.terms': 'Условия использования',
                'footer.contact': 'Связаться с нами',
                
                // Уведомления
                'notification.loginSuccess': 'Вход выполнен успешно!',
                'notification.loginError': 'Ошибка входа. Проверьте данные.',
                'notification.registerSuccess': 'Регистрация успешна!',
                'notification.registerError': 'Ошибка регистрации.',
                'notification.logoutSuccess': 'Вы вышли из системы',
                'notification.paymentSuccess': 'Оплата успешно завершена!',
                'notification.paymentError': 'Ошибка оплаты',
                'notification.profileUpdated': 'Профиль обновлен',
                'notification.qrGenerated': 'QR-код сгенерирован',
                'notification.serverError': 'Ошибка соединения с сервером',
                'notification.noConnection': 'Нет соединения с сервером',
                'notification.sessionExpired': 'Сессия истекла. Пожалуйста, войдите снова.',
                
                // Тема
                'theme.light': 'Светлая тема',
                'theme.dark': 'Темная тема',
                'theme.toggle': 'Переключить тему'
            },
            en: {
                // Navigation
                'nav.home': 'Home',
                'nav.fuel': 'Fuel',
                'nav.payment': 'QR Payment',
                'nav.stations': 'Stations',
                'nav.profile': 'Profile',
                
                // Authentication
                'auth.login': 'Login',
                'auth.register': 'Register',
                'auth.logout': 'Logout',
                'auth.phone': 'Phone Number',
                'auth.password': 'Password',
                'auth.confirmPassword': 'Confirm Password',
                'auth.fullName': 'Full Name',
                'auth.remember': 'Remember me',
                'auth.forgot': 'Forgot password?',
                'auth.noAccount': 'No account?',
                'auth.haveAccount': 'Already have an account?',
                'auth.acceptTerms': 'I accept the',
                'auth.passwordHint': 'Minimum 6 characters',
                
                // Hero
                'hero.title': 'Refueling Made Easier',
                'hero.subtitle': 'Fast QR payment, bonus system and convenient service',
                'hero.fuelTypes': 'Fuel types',
                'hero.stations': 'Stations in network',
                'hero.customers': 'Happy customers',
                'hero.startRefuel': 'Start Refueling',
                'hero.learnMore': 'Learn More',
                
                // Fuel
                'fuel.title': 'Fuel Prices',
                'fuel.ai92': 'AI-92',
                'fuel.ai95': 'AI-95',
                'fuel.ai98': 'AI-98',
                'fuel.ai100': 'AI-100',
                'fuel.diesel': 'Diesel',
                'fuel.dtk5': 'DT-K5',
                'fuel.perLiter': 'BYN/liter',
                'fuel.available': 'Available',
                'fuel.low': 'Low',
                'fuel.unavailable': 'Unavailable',
                
                // Payment
                'payment.title': 'QR Code Payment',
                'payment.scanCode': 'Scan the code at the pump',
                'payment.generateQr': 'Generate QR',
                'payment.scan': 'Scan QR',
                'payment.howItWorks': 'How it works:',
                'payment.step1': 'Approach the gas pump',
                'payment.step2': 'Scan the QR code on the pump',
                'payment.step3': 'Select fuel type and amount',
                'payment.step4': 'Pay conveniently',
                'payment.step5': 'Start refueling',
                'payment.payment': 'Payment',
                'payment.scanQr': 'Scan QR Code',
                'payment.switchCamera': 'Switch Camera',
                'payment.toggleCamera': 'Toggle Camera',
                'payment.orEnterCode': 'Or enter code manually:',
                'payment.continue': 'Continue',
                'payment.scannerPlaceholder': 'Use device camera to scan QR code',
                
                // Stations
                'stations.title': 'Our Stations on Map',
                'stations.map': 'Station Map',
                'stations.selectStation': 'Select station from list',
                'stations.list': 'Stations List',
                
                // Profile
                'profile.title': 'Personal Account',
                'profile.profile': 'Profile',
                'profile.history': 'History',
                'profile.bonuses': 'Bonuses',
                'profile.settings': 'Settings',
                'profile.welcome': 'Welcome!',
                'profile.selectTab': 'Select section from left menu',
                'profile.fullName': 'Full Name',
                'profile.phone': 'Phone',
                'profile.balance': 'Bonus Balance',
                'profile.totalLiters': 'Total Refueled',
                'profile.totalSpent': 'Total Spent',
                'profile.registrationDate': 'Registration Date',
                'profile.transactionHistory': 'Transaction History',
                'profile.noTransactions': 'No transactions',
                'profile.date': 'Date',
                'profile.fuelType': 'Fuel Type',
                'profile.amount': 'Amount',
                'profile.price': 'Price',
                'profile.total': 'Total',
                'profile.paymentMethod': 'Payment Method',
                'profile.status': 'Status',
                'profile.bonusRules': 'Bonus System Rules',
                'profile.rule1': '1% from each purchase',
                'profile.rule1desc': 'You get 1% of each refueling amount as bonuses',
                'profile.rule2': 'Pay with bonuses',
                'profile.rule2desc': 'You can pay up to 50% of purchase with bonuses',
                'profile.rule3': 'No expiration',
                'profile.rule3desc': 'Bonuses never expire',
                'profile.settingsTitle': 'Account Settings',
                'profile.personalData': 'Personal Data',
                'profile.security': 'Security',
                'profile.currentPassword': 'Current Password',
                'profile.newPassword': 'New Password',
                'profile.dangerZone': 'Danger Zone',
                'profile.deleteWarning': 'Account deletion cannot be undone',
                'profile.deleteAccount': 'Delete Account',
                'profile.saveChanges': 'Save Changes',
                'profile.changePassword': 'Change Password',
                
                // Common
                'common.loading': 'Loading...',
                'common.save': 'Save',
                'common.cancel': 'Cancel',
                'common.confirm': 'Confirm',
                'common.back': 'Back',
                'common.next': 'Next',
                'common.yes': 'Yes',
                'common.no': 'No',
                'success': 'Success',
                'error': 'Error',
                'warning': 'Warning',
                'info': 'Information',
                
                // Footer
                'footer.slogan': 'Quality fuel and service at the highest level',
                'footer.contacts': 'Contacts',
                'footer.24h': '24/7',
                'footer.address': 'Minsk, Toplivnaya st., 1',
                'footer.download': 'Download App',
                'footer.rights': 'All rights reserved.',
                'footer.privacy': 'Privacy Policy',
                'footer.terms': 'Terms of Use',
                'footer.contact': 'Contact Us',
                
                // Notifications
                'notification.loginSuccess': 'Login successful!',
                'notification.loginError': 'Login error. Check your data.',
                'notification.registerSuccess': 'Registration successful!',
                'notification.registerError': 'Registration error.',
                'notification.logoutSuccess': 'You have logged out',
                'notification.paymentSuccess': 'Payment completed successfully!',
                'notification.paymentError': 'Payment error',
                'notification.profileUpdated': 'Profile updated',
                'notification.qrGenerated': 'QR code generated',
                'notification.serverError': 'Server connection error',
                'notification.noConnection': 'No server connection',
                'notification.sessionExpired': 'Session expired. Please login again.',
                
                // Theme
                'theme.light': 'Light Theme',
                'theme.dark': 'Dark Theme',
                'theme.toggle': 'Toggle Theme'
            }
        };
        
        this.init();
    }
    
    init() {
        this.setLanguage(this.currentLang);
        this.setupEventListeners();
    }
    
    setupEventListeners() {
        const languageSelect = document.getElementById('languageSelect');
        if (languageSelect) {
            languageSelect.value = this.currentLang;
            languageSelect.addEventListener('change', (e) => {
                this.setLanguage(e.target.value);
            });
        }
    }
    
    setLanguage(lang) {
        this.currentLang = lang;
        localStorage.setItem('language', lang);
        
        // Обновляем все элементы с data-i18n атрибутом
        document.querySelectorAll('[data-i18n]').forEach(element => {
            const key = element.getAttribute('data-i18n');
            if (this.translations[lang] && this.translations[lang][key]) {
                if (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA') {
                    element.placeholder = this.translations[lang][key];
                } else if (element.tagName === 'OPTION') {
                    element.textContent = this.translations[lang][key];
                } else {
                    element.textContent = this.translations[lang][key];
                }
            }
        });
        
        // Обновляем тег html
        document.documentElement.lang = lang;
        
        // Обновляем выбранный язык в селекторе
        const languageSelect = document.getElementById('languageSelect');
        if (languageSelect) {
            languageSelect.value = lang;
        }
    }
    
    getTranslation(key) {
        return this.translations[this.currentLang][key] || key;
    }
    
    translate(key) {
        return this.getTranslation(key);
    }
}

window.i18n = new Internationalization();