+function () {
    const webpack = require('webpack');
    const ContextReplacementPlugin = webpack.ContextReplacementPlugin;
    const TerserPlugin = require("terser-webpack-plugin");
    const getCpusLength = require("get_cpus_length");

    config.optimization = {
        usedExports: true,
        splitChunks: {
            chunks: 'all',
            filename: 'modules.js'
        },
        minimize: true,
        minimizer: [new TerserPlugin({
            parallel: getCpusLength(),
            terserOptions: {
                compress: {
                    passes: 2
                }
            }
        })]
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
