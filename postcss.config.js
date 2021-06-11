module.exports = {
  plugins: [
    // Tailwind CSS recommends using the nesting plugin bundled with it because
    // it acts as a compatibility layer that understands custom TW's syntax
    // like @apply and @screen.
    require('tailwindcss/nesting'),

    require('tailwindcss'),
    require('autoprefixer'),

    // Ensure the final CSS is as small as possible for production environments.
    require('cssnano')({
      preset: [
        'default',
        {
          discardComments: {
            removeAll: true
          }
        }
      ]
    })
  ]
}
