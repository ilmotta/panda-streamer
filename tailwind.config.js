module.exports = {
  content: [
    'build/public/index.html',
    'src/main/clojure/acme/web/**/*.cljs'
  ],
  safelist: [{ pattern: /^co-/ }],
  // Avoid using JIT because it has rough edges, .e.g Tailwind CSS nesting
  // doesn't work at all.
  //
  // mode: 'jit',
  darkMode: 'media',
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
