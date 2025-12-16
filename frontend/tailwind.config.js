/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {},
  },
  plugins: [require("daisyui")],
  daisyui: {
    themes: [
      {
        light: {
          ...require("daisyui/src/theming/themes")["light"],
          "primary": "#3B82F6", // A vibrant, modern blue for user messages and primary actions
          "primary-focus": "#2563EB", // Darker blue on focus
          "primary-content": "#ffffff",
          "secondary": "#F0F4F8", // A very light, clean grey for assistant messages
          "secondary-focus": "#DDE2E7",
          "secondary-content": "#1F2937", // Dark text for readability
          "base-100": "#ffffff", // Main background white
          "base-200": "#F9FAFB", // Light grey for subtle sections
          "base-300": "#E5E7EB", // Slightly darker grey for borders/dividers
          "neutral": "#4B5563", // Darker grey for neutral elements
          "neutral-focus": "#374151",
          "neutral-content": "#ffffff",
          "info": "#67E8F9", // A light blue for info (optional)
          "success": "#22C55E", // Green for success
          "warning": "#FBBF24", // Amber for warning
          "error": "#EF4444", // Red for error
        },
      },
      "dark", // Keep dark theme as an option
    ],
  },
}
