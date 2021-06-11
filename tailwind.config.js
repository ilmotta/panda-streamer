module.exports = {
  // Tailwind uses PurgeCSS behind the scenes.
  purge: {
    preserveHtmlElements: true,
    content: ['build/public/index.html', 'src/main/clojure/acme/web/**/*.cljs'],
    safelist: {
      standard: [/^co-/, /^hover:bg-purple/]
    },
    options: { keyframes: false, fontFace: true }
  },
  // Avoid using JIT because it has rough edges, .e.g Tailwind CSS nesting
  // doesn't work at all.
  //
  // mode: 'jit',
  darkMode: false, // or 'media' or 'class'
  theme: {
    extend: {}
  },
  variants: {
    extend: {
      opacity: ['disabled'],
      backgroundColor: ['disabled', 'odd'],
      textColor: ['disabled'],
      cursor: ['disabled']
    }
  },
  plugins: []
}
