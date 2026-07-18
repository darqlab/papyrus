(function () {
  function updateThemeIcon() {
    const moon = document.getElementById('theme-icon-moon');
    const sun = document.getElementById('theme-icon-sun');
    if (!moon || !sun) return;
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    // Icon shown = action the button performs next (dark shows sun = "switch to light").
    moon.style.display = isDark ? 'none' : '';
    sun.style.display = isDark ? '' : 'none';
  }

  async function checkHealth() {
    const dot = document.getElementById('health-dot');
    const lbl = document.getElementById('health-label');
    if (!dot || !lbl) return;
    try {
      const res = await fetch('/actuator/health', { cache: 'no-store' });
      const data = await res.json();
      dot.className = data.status === 'UP' ? 'health-dot health-ok' : 'health-dot health-warn';
      lbl.textContent = data.status === 'UP' ? 'Online' : 'Degraded';
    } catch {
      dot.className = 'health-dot health-err';
      lbl.textContent = 'Offline';
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    updateThemeIcon();
    const btn = document.getElementById('theme-toggle');
    if (btn) {
      btn.addEventListener('click', function () {
        const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
        document.documentElement.setAttribute('data-theme', isDark ? 'light' : 'dark');
        localStorage.setItem('papyrus-theme', isDark ? 'light' : 'dark');
        updateThemeIcon();
      });
    }
    checkHealth();
    setInterval(checkHealth, 60_000);
  });
})();
