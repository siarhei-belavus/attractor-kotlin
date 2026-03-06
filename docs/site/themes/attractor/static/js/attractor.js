// FOUC prevention is handled by the inline script in head.html.
// This file handles toggle interaction and mobile nav only.

function toggleTheme() {
  var current = document.documentElement.getAttribute('data-theme') || 'light';
  var next = current === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', next);
  document.documentElement.style.colorScheme = next;
  localStorage.setItem('attractor-theme', next);
  var btn = document.getElementById('themeToggle');
  if (btn) btn.textContent = next === 'dark' ? '\u263d' : '\u2600'; // moon / sun
}

document.addEventListener('DOMContentLoaded', function () {
  // Sync toggle icon to current theme
  var theme = document.documentElement.getAttribute('data-theme') || 'light';
  var btn = document.getElementById('themeToggle');
  if (btn) btn.textContent = theme === 'dark' ? '\u263d' : '\u2600';

  // Mobile nav toggle
  var navToggle = document.getElementById('navToggle');
  var nav = document.getElementById('siteNav');
  if (navToggle && nav) {
    navToggle.addEventListener('click', function () {
      nav.classList.toggle('nav-open');
    });
    // Close nav when a link is tapped (mobile UX)
    nav.querySelectorAll('a').forEach(function (link) {
      link.addEventListener('click', function () {
        nav.classList.remove('nav-open');
      });
    });
  }
});
