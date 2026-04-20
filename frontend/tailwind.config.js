/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        mq: {
          blue: '#0062ff',
          dark: '#161616',
          gray: '#393939',
        }
      },
      fontFamily: {
        mono: ['IBM Plex Mono', 'Consolas', 'Monaco', 'monospace'],
      }
    },
  },
  plugins: [],
}
