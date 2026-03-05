import winston from 'winston';

const { combine, timestamp, colorize, printf, errors } = winston.format;

const logFormat = printf(({ level, message, timestamp: ts, stack, ...meta }) => {
  let log = `${ts} [${level}] ${message}`;
  if (Object.keys(meta).length) log += ` ${JSON.stringify(meta)}`;
  if (stack) log += `\n${stack}`;
  return log;
});

export const logger = winston.createLogger({
  level: process.env['NODE_ENV'] === 'production' ? 'info' : 'debug',
  format: combine(
    errors({ stack: true }),
    timestamp({ format: 'HH:mm:ss' }),
    colorize(),
    logFormat,
  ),
  transports: [
    new winston.transports.Console(),
    new winston.transports.File({
      filename: 'logs/error.log',
      level: 'error',
      format: combine(errors({ stack: true }), timestamp(), logFormat),
    }),
    new winston.transports.File({
      filename: 'logs/combined.log',
      format: combine(timestamp(), logFormat),
    }),
  ],
});
