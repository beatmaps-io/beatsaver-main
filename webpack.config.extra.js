//let config = require('./build/js/node_modules/BeatMaps/webpack.config');

let config = {
  plugins: [],
  optimization: {
    usedExports: true,
    splitChunks: {
      chunks: 'all',
      filename: 'modules.js'
    }
  }
};

;(function(config) {
  const webpack = require('./build/js/node_modules/webpack');
    config.plugins.push(new webpack.ContextReplacementPlugin(/moment[\/\\]locale$/, /en\-gb/))
})(config);

module.exports = config;