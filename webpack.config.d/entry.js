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

    const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
    config.plugins.push(new BundleAnalyzerPlugin({
        analyzerMode: 'static',
        reportFilename: '../../../../reports/webpack/BeatMaps/BeatMaps.js.report.html',
        generateStatsFile: true,
        statsFilename: '../../../../reports/webpack/BeatMaps/BeatMaps.js.stats.json',
        openAnalyzer: false
    }));
}()
