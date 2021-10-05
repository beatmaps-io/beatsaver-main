+function () {
    const webpack = require('webpack');
    const ContextReplacementPlugin = webpack.ContextReplacementPlugin;

    config.optimization = {
        usedExports: true,
        splitChunks: {
            chunks: 'all',
            filename: 'modules.js'
        }
    };
    config.plugins.push(new ContextReplacementPlugin(/moment[\/\\]locale$/, /en\-gb/));
}()