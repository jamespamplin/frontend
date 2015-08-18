'use strict';
module.exports = function(grunt, options) {
    return {
        npmInstall: {
            command: 'npm prune && npm install',
            options: {
                execOptions: {
                    cwd: 'facia-tool/public'
                }
            }
        },

        jspmInstall: {
            command: './node_modules/.bin/jspm install'
        }
    };
};
