import React, { useState, useEffect } from 'react';

// Tarayıcıdaki varsayılan tema tercihini kontrol eden fonksiyon
const isDefaultDark = () => window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;

// localStorage'dan temayı okuyan fonksiyon
const getInitialTheme = () => {
  const savedTheme = localStorage.getItem('theme');
  if (savedTheme) {
    return savedTheme;
  }
  return isDefaultDark() ? 'dark' : 'light';
};

function ThemeToggle() {
  const [theme, setTheme] = useState(getInitialTheme);

  useEffect(() => {
    if (theme === 'light') {
      document.body.classList.add('light-theme');
    } else {
      document.body.classList.remove('light-theme');
    }
    localStorage.setItem('theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme(prevTheme => (prevTheme === 'light' ? 'dark' : 'light'));
  };

  return (
    <button onClick={toggleTheme} className="theme-toggle-button" title="Temayı Değiştir">
      {theme === 'light' ? '🌙' : '☀️'}
    </button>
  );
}

export default ThemeToggle;